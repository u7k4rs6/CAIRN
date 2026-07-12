package dev.cairn.transfer;

import dev.cairn.vcs.dag.GenerationNumbers;
import dev.cairn.vcs.dag.GenerationStore;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.GitObject;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.pack.PackReader;
import dev.cairn.vcs.ref.RefStore;
import dev.cairn.vcs.store.ObjectStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The server side of {@code git-receive-pack}: push. Parses the client's proposed ref
 * updates, unpacks and stores the accompanying pack, then authorizes every update
 * through a caller-supplied {@link RefUpdateAuthorizer} before writing anything
 * (security doc, section 4.3): "Only after all updates pass are refs written. A
 * violating push is rejected atomically."
 *
 * <p>The authorizer is a plain functional interface, not a dependency on any
 * particular permission model, so this class (which only {@code cairn-vcs} sits
 * beneath) never depends on {@code cairn-api}'s domain or persistence layer; M6
 * supplies the real implementation, wired in from the platform side.
 */
public final class ReceivePackHandler {

    public record RefUpdateCommand(String ref, Optional<ObjectId> oldId, Optional<ObjectId> newId) {
        public boolean isDelete() {
            return newId.isEmpty();
        }
    }

    public record Outcome(boolean unpackOk, List<String> refResults) {
    }

    /** Decides whether one proposed ref update may proceed. Implemented on the platform side (M6: {@code effective_role} + branch protection). */
    public interface RefUpdateAuthorizer {
        Decision authorize(RefUpdateCommand command);

        record Decision(boolean allowed, String reason) {
            public static Decision allow() {
                return new Decision(true, null);
            }

            public static Decision deny(String reason) {
                return new Decision(false, reason);
            }
        }
    }

    /** Allows every update: the M5-era behavior, for tests and contexts with no permission model wired in yet. */
    public static final RefUpdateAuthorizer ALLOW_ALL = command -> RefUpdateAuthorizer.Decision.allow();

    private ReceivePackHandler() {
    }

    public static List<RefUpdateCommand> parseCommands(InputStream in) {
        List<RefUpdateCommand> commands = new ArrayList<>();
        boolean first = true;
        while (true) {
            var payload = PktLine.read(in);
            if (payload == null || payload.isEmpty()) {
                break;
            }
            String line = new String(payload.get(), StandardCharsets.UTF_8);
            if (line.endsWith("\n")) {
                line = line.substring(0, line.length() - 1);
            }
            if (first) {
                int nul = line.indexOf('\0');
                if (nul >= 0) {
                    line = line.substring(0, nul);
                }
                first = false;
            }
            String[] parts = line.split(" ", 3);
            Optional<ObjectId> oldId = parts[0].equals(RefAdvertisement.ZERO_ID) ? Optional.empty() : Optional.of(ObjectId.fromHex(parts[0]));
            Optional<ObjectId> newId = parts[1].equals(RefAdvertisement.ZERO_ID) ? Optional.empty() : Optional.of(ObjectId.fromHex(parts[1]));
            commands.add(new RefUpdateCommand(parts[2], oldId, newId));
        }
        return commands;
    }

    /** Reads and applies the request: parses commands, unpacks the trailing pack (if any), authorizes, then writes refs. */
    public static Outcome handle(ObjectStore store, RefStore refs, GenerationStore generations, InputStream requestBody,
                                  RefUpdateAuthorizer authorizer) {
        List<RefUpdateCommand> commands = parseCommands(requestBody);
        byte[] packBytes;
        try {
            packBytes = requestBody.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        boolean unpackOk = true;
        if (packBytes.length > 0) {
            try {
                Map<ObjectId, GitObject> objects = PackReader.read(packBytes);
                for (Map.Entry<ObjectId, GitObject> entry : objects.entrySet()) {
                    store.put(entry.getValue());
                }
                for (Map.Entry<ObjectId, GitObject> entry : objects.entrySet()) {
                    if (entry.getValue() instanceof Commit) {
                        GenerationNumbers.computeAndStore(store, generations, entry.getKey());
                    }
                }
            } catch (RuntimeException e) {
                unpackOk = false;
            }
        }

        List<String> results = new ArrayList<>();
        if (!unpackOk) {
            for (RefUpdateCommand command : commands) {
                results.add("ng " + command.ref() + " unpack failed");
            }
            return new Outcome(false, results);
        }

        Map<RefUpdateCommand, RefUpdateAuthorizer.Decision> decisions = new LinkedHashMap<>();
        boolean allAllowed = true;
        for (RefUpdateCommand command : commands) {
            RefUpdateAuthorizer.Decision decision = authorizer.authorize(command);
            decisions.put(command, decision);
            allAllowed &= decision.allowed();
        }

        if (!allAllowed) {
            for (RefUpdateCommand command : commands) {
                RefUpdateAuthorizer.Decision decision = decisions.get(command);
                results.add(decision.allowed()
                        ? "ng " + command.ref() + " transaction failed"
                        : "ng " + command.ref() + " " + decision.reason());
            }
            return new Outcome(true, results);
        }

        for (RefUpdateCommand command : commands) {
            if (command.isDelete()) {
                refs.delete(command.ref());
            } else {
                refs.update(command.ref(), command.newId().orElseThrow());
            }
            results.add("ok " + command.ref());
        }
        return new Outcome(true, results);
    }

    public static byte[] buildReportStatus(Outcome outcome) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PktLine.encode("unpack " + (outcome.unpackOk() ? "ok" : "unpack-failed") + "\n"));
        for (String result : outcome.refResults()) {
            out.writeBytes(PktLine.encode(result + "\n"));
        }
        out.writeBytes(PktLine.flush());
        return out.toByteArray();
    }
}

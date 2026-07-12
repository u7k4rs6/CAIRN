package dev.cairn.transfer;

/**
 * The capability strings Cairn advertises in ref advertisements. Deliberately
 * minimal; see {@link UploadPackHandler}'s Javadoc. Notably absent: {@code ofs-delta}.
 * Advertising it on receive-pack would tell a real Git client it may push an
 * OFS_DELTA-encoded pack, but {@code PackReader} only understands REF_DELTA
 * (architecture doc, section 4.2's simplification carried through to the wire);
 * omitting the capability keeps the client on REF_DELTA, which we can actually read.
 */
public final class TransferCapabilities {

    public static final String UPLOAD_PACK = "agent=cairn/0.1";
    public static final String RECEIVE_PACK = "report-status delete-refs agent=cairn/0.1";

    private TransferCapabilities() {
    }
}

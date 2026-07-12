package dev.cairn.api.collab;

/** An issue's lifecycle: simpler than a PR's, but still a State pattern instance (architecture doc, section 6.1). */
public enum IssueState {
    OPEN {
        @Override
        public IssueState close() {
            return CLOSED;
        }
    },
    CLOSED {
        @Override
        public IssueState reopen() {
            return OPEN;
        }
    };

    public IssueState close() {
        throw illegal("close");
    }

    public IssueState reopen() {
        throw illegal("reopen");
    }

    private IllegalStateException illegal(String action) {
        return new IllegalStateException("cannot " + action + " an issue in state " + this);
    }
}

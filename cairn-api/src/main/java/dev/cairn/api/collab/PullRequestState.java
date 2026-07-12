package dev.cairn.api.collab;

/**
 * The PR lifecycle (architecture doc, section 6.1): each state is a type (here, an
 * enum constant with its own method overrides) that answers which actions are legal
 * from it and what state comes next, so an illegal transition, merging a closed or
 * already-merged PR, is a thrown exception from the state itself rather than a
 * scattered {@code if} check duplicated at every call site. {@code MERGED} overrides
 * nothing: every action on it falls through to the base "not legal" implementation,
 * making it structurally terminal.
 */
public enum PullRequestState {
    DRAFT {
        @Override
        public PullRequestState markReady() {
            return OPEN;
        }

        @Override
        public PullRequestState close() {
            return CLOSED;
        }
    },
    OPEN {
        @Override
        public PullRequestState requestReview() {
            return REVIEW_REQUESTED;
        }

        @Override
        public PullRequestState approve() {
            return APPROVED;
        }

        @Override
        public PullRequestState requestChanges() {
            return CHANGES_REQUESTED;
        }

        @Override
        public PullRequestState merge() {
            return MERGED;
        }

        @Override
        public PullRequestState close() {
            return CLOSED;
        }
    },
    REVIEW_REQUESTED {
        @Override
        public PullRequestState approve() {
            return APPROVED;
        }

        @Override
        public PullRequestState requestChanges() {
            return CHANGES_REQUESTED;
        }

        @Override
        public PullRequestState merge() {
            return MERGED;
        }

        @Override
        public PullRequestState close() {
            return CLOSED;
        }
    },
    APPROVED {
        @Override
        public PullRequestState requestChanges() {
            return CHANGES_REQUESTED;
        }

        @Override
        public PullRequestState merge() {
            return MERGED;
        }

        @Override
        public PullRequestState close() {
            return CLOSED;
        }
    },
    CHANGES_REQUESTED {
        @Override
        public PullRequestState approve() {
            return APPROVED;
        }

        @Override
        public PullRequestState requestReview() {
            return REVIEW_REQUESTED;
        }

        @Override
        public PullRequestState merge() {
            return MERGED;
        }

        @Override
        public PullRequestState close() {
            return CLOSED;
        }
    },
    MERGED {
        // Fully terminal: every action below throws.
    },
    CLOSED {
        @Override
        public PullRequestState reopen() {
            return OPEN;
        }
    };

    public PullRequestState markReady() {
        throw illegal("mark ready");
    }

    public PullRequestState requestReview() {
        throw illegal("request review");
    }

    public PullRequestState approve() {
        throw illegal("approve");
    }

    public PullRequestState requestChanges() {
        throw illegal("request changes");
    }

    public PullRequestState merge() {
        throw illegal("merge");
    }

    public PullRequestState close() {
        throw illegal("close");
    }

    public PullRequestState reopen() {
        throw illegal("reopen");
    }

    private IllegalStateException illegal(String action) {
        return new IllegalStateException("cannot " + action + " a pull request in state " + this);
    }
}

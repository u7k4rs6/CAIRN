package dev.cairn.api.collab;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Architecture doc, section 6.1: illegal transitions (merging a closed or already-merged PR) must be structurally impossible. */
class PullRequestStateTest {

    @Test
    void draftMovesToOpenThenCanMerge() {
        assertThat(PullRequestState.DRAFT.markReady()).isEqualTo(PullRequestState.OPEN);
        assertThat(PullRequestState.OPEN.merge()).isEqualTo(PullRequestState.MERGED);
    }

    @Test
    void fullReviewCycle() {
        var state = PullRequestState.OPEN.requestReview();
        assertThat(state).isEqualTo(PullRequestState.REVIEW_REQUESTED);
        state = state.requestChanges();
        assertThat(state).isEqualTo(PullRequestState.CHANGES_REQUESTED);
        state = state.approve();
        assertThat(state).isEqualTo(PullRequestState.APPROVED);
        assertThat(state.merge()).isEqualTo(PullRequestState.MERGED);
    }

    @Test
    void mergedIsFullyTerminal() {
        assertThatThrownBy(() -> PullRequestState.MERGED.merge()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PullRequestState.MERGED.close()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PullRequestState.MERGED.reopen()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PullRequestState.MERGED.approve()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotMergeAClosedPullRequest() {
        assertThatThrownBy(() -> PullRequestState.CLOSED.merge())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot merge");
    }

    @Test
    void closedCanReopenBackToOpen() {
        assertThat(PullRequestState.CLOSED.reopen()).isEqualTo(PullRequestState.OPEN);
    }

    @Test
    void draftCannotMergeDirectly() {
        assertThatThrownBy(() -> PullRequestState.DRAFT.merge()).isInstanceOf(IllegalStateException.class);
    }
}

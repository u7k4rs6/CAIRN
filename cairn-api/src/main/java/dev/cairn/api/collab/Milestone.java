package dev.cairn.api.collab;

import dev.cairn.api.domain.Repo;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** PRD Tier 2: a repo-scoped grouping of issues (and, on paper, pull requests) toward a shared target. */
@Entity
@Table(name = "milestones")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Repo repo;

    private String title;
    private String description;
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    private MilestoneState state = MilestoneState.OPEN;

    protected Milestone() {
    }

    public Milestone(Repo repo, String title, String description, Instant dueAt) {
        this.repo = repo;
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
    }

    public Long id() {
        return id;
    }

    public Repo repo() {
        return repo;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Instant dueAt() {
        return dueAt;
    }

    public MilestoneState state() {
        return state;
    }

    public void transitionTo(MilestoneState next) {
        this.state = next;
    }
}

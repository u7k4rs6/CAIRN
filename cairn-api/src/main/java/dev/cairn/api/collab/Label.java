package dev.cairn.api.collab;

import dev.cairn.api.domain.Repo;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** PRD Tier 2: a repo-scoped tag applied to issues (and, on paper, pull requests; see DECISIONS.md for the PR-side scope cut). */
@Entity
@Table(name = "labels")
public class Label {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Repo repo;

    private String name;

    /** A hex color without the leading '#' (e.g. {@code "d73a4a"}), the same convention GitHub labels use. */
    private String color;

    protected Label() {
    }

    public Label(Repo repo, String name, String color) {
        this.repo = repo;
        this.name = name;
        this.color = color;
    }

    public Long id() {
        return id;
    }

    public Repo repo() {
        return repo;
    }

    public String name() {
        return name;
    }

    public String color() {
        return color;
    }
}

package ru.yandex.practicum.mybank.accounts.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

@Entity
@Table(name = "outbox")
public class OutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected OutboxEntry() {}

    public OutboxEntry(String login, String kind, String message) {
        this.login = login;
        this.kind = kind;
        this.message = message;
        this.createdAt = OffsetDateTime.now();
        this.attempts = 0;
    }

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getKind() { return kind; }
    public String getMessage() { return message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public int getAttempts() { return attempts; }
    public int getVersion() { return version; }

    public void markSent() {
        this.sentAt = OffsetDateTime.now();
    }

    public void incrementAttempts() {
        this.attempts++;
    }
}

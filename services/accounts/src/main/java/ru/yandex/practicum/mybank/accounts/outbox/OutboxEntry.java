package ru.yandex.practicum.mybank.accounts.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, unique = true)
    private UUID eventId;

    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "processing_at")
    private OffsetDateTime processingAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected OutboxEntry() {}

    public OutboxEntry(String login, String kind, BigDecimal amount) {
        this.eventId = UUID.randomUUID();
        this.login = login;
        this.kind = kind;
        this.amount = amount;
        this.createdAt = OffsetDateTime.now();
        this.status = OutboxStatus.NEW;
        this.attempts = 0;
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getLogin() { return login; }
    public String getKind() { return kind; }
    public BigDecimal getAmount() { return amount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OutboxStatus getStatus() { return status; }
    public OffsetDateTime getProcessingAt() { return processingAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public int getAttempts() { return attempts; }
    public int getVersion() { return version; }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.processingAt = OffsetDateTime.now();
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = OffsetDateTime.now();
    }

    public void markFailed() {
        this.status = OutboxStatus.NEW;   // back to NEW so the next poll retries it
        this.attempts++;
    }
}

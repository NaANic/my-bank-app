package ru.yandex.practicum.mybank.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "message", nullable = false)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Notification() {}

    public Notification(String eventId, String login, String kind, String message) {
        this.eventId = eventId;
        this.login = login;
        this.kind = kind;
        this.message = message;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getLogin() { return login; }
    public String getKind() { return kind; }
    public String getMessage() { return message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

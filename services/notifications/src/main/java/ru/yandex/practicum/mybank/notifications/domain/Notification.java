package ru.yandex.practicum.mybank.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Notification() {}

    public Notification(String login, String kind, String message) {
        this.login = login;
        this.kind = kind;
        this.message = message;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getKind() { return kind; }
    public String getMessage() { return message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

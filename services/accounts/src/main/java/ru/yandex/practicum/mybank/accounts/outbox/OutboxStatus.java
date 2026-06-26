package ru.yandex.practicum.mybank.accounts.outbox;

public enum OutboxStatus {
    NEW, PROCESSING, SENT, FAILED
}

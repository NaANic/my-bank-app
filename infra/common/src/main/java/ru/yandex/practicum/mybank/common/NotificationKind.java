package ru.yandex.practicum.mybank.common;

/** Type of balance change that produced a notification. */
public enum NotificationKind {
    BALANCE_CREDIT,
    BALANCE_DEBIT,
    BALANCE_TRANSFER_OUT,
    BALANCE_TRANSFER_IN
}

package ru.yandex.practicum.mybank.notifications.api;

import jakarta.validation.constraints.NotBlank;

public record NotificationRequest(
        @NotBlank String login,
        @NotBlank String kind,
        @NotBlank String message
) {}

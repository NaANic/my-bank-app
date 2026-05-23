package ru.yandex.practicum.mybank.accounts.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull @Past LocalDate dob
) {}

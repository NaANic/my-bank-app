package ru.yandex.practicum.mybankfront.client;

import java.time.LocalDate;

public record ProfileUpdate(String firstName, String lastName, LocalDate dob) {}

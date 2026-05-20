package ru.yandex.practicum.mybank.cash.client;

import org.springframework.http.HttpStatusCode;

public class AccountsServiceException extends RuntimeException {

    private final HttpStatusCode status;
    private final String body;

    public AccountsServiceException(HttpStatusCode status, String body) {
        super("Accounts returned " + status.value() + ": " + body);
        this.status = status;
        this.body = body;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }
}

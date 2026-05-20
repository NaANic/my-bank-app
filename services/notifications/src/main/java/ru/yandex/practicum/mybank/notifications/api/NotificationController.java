package ru.yandex.practicum.mybank.notifications.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.mybank.notifications.domain.Notification;
import ru.yandex.practicum.mybank.notifications.domain.NotificationRepository;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public void send(@Valid @RequestBody NotificationRequest req) {
        Notification persisted = repository.save(new Notification(req.login(), req.kind(), req.message()));
        log.info("[notification#{}] login={} kind={} message={}",
                persisted.getId(), persisted.getLogin(), persisted.getKind(), persisted.getMessage());
    }
}

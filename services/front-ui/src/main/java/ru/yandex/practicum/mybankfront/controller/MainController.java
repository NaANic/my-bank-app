package ru.yandex.practicum.mybankfront.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;
import ru.yandex.practicum.mybankfront.client.AccountsClient;
import ru.yandex.practicum.mybankfront.client.CashClient;
import ru.yandex.practicum.mybankfront.client.Profile;
import ru.yandex.practicum.mybankfront.client.ProfileUpdate;
import ru.yandex.practicum.mybankfront.client.TransferClient;
import ru.yandex.practicum.mybankfront.controller.dto.AccountDto;
import ru.yandex.practicum.mybankfront.controller.dto.CashAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class MainController {

    private final AccountsClient accounts;
    private final CashClient cash;
    private final TransferClient transfer;
    private final ObjectMapper objectMapper;

    public MainController(AccountsClient accounts, CashClient cash, TransferClient transfer, ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.cash = cash;
        this.transfer = transfer;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/account";
    }

    @GetMapping("/account")
    public String getAccount(Model model) {
        return safeRender(model, null, null);
    }

    @PostMapping("/account")
    public String editAccount(Model model,
                              @RequestParam("name") String name,
                              @RequestParam("birthdate") LocalDate birthdate) {
        List<String> errors = validateProfile(name, birthdate);
        if (!errors.isEmpty()) {
            return safeRender(model, errors, null);
        }
        String[] parts = name.trim().split("\\s+", 2);
        ProfileUpdate update = new ProfileUpdate(parts[1], parts[0], birthdate);
        Profile saved;
        try {
            saved = accounts.updateMe(update);
        } catch (RestClientResponseException e) {
            return safeRender(model, List.of(formatBackendError(e)), null);
        } catch (CallNotPermittedException e) {
            return safeRender(model, List.of("Сервис временно недоступен"), null);
        }
        return render(model, saved, null, "Данные сохранены");
    }

    @PostMapping("/cash")
    public String editCash(Model model,
                           @RequestParam("value") int value,
                           @RequestParam("action") CashAction action) {
        if (value <= 0) {
            return safeRender(model, List.of("Сумма должна быть положительной"), null);
        }
        BigDecimal amount = BigDecimal.valueOf(value);
        try {
            Profile updated = switch (action) {
                case PUT -> cash.deposit(amount);
                case GET -> cash.withdraw(amount);
            };
            String info = action == CashAction.PUT
                    ? "Счёт пополнен на " + value + " руб."
                    : "Снято " + value + " руб.";
            return render(model, updated, null, info);
        } catch (RestClientResponseException e) {
            return safeRender(model, List.of(formatBackendError(e)), null);
        } catch (CallNotPermittedException e) {
            return safeRender(model, List.of("Сервис временно недоступен"), null);
        }
    }

    private String formatBackendError(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        try {
            Map<?, ?> parsed = objectMapper.readValue(body, Map.class);
            Object code = parsed.get("error");
            if ("insufficient_funds".equals(code)) {
                return "Недостаточно средств на счёте";
            }
            Object message = parsed.get("message");
            if (message != null) {
                return message.toString();
            }
        } catch (Exception ignored) {
        }
        return "Ошибка: " + e.getStatusCode();
    }

    @PostMapping("/transfer")
    public String transfer(Model model,
                           @RequestParam("value") int value,
                           @RequestParam("login") String login) {
        if (value <= 0) {
            return render(model, accounts.getMe(), List.of("Сумма перевода должна быть положительной"), null);
        }
        if (login == null || login.isBlank()) {
            return safeRender(model, List.of("Выберите получателя"), null);
        }
        try {
            Profile updated = transfer.transfer(login, BigDecimal.valueOf(value));
            return render(model, updated, null, "Переведено " + value + " руб. пользователю " + login);
        } catch (RestClientResponseException e) {
            return safeRender(model, List.of(formatBackendError(e)), null);
        } catch (CallNotPermittedException e) {
            return safeRender(model, List.of("Сервис временно недоступен"), null);
        }
    }

    private String safeRender(Model model, List<String> existingErrors, String info) {
        Profile me;
        List<String> errors = existingErrors == null ? new ArrayList<>() : new ArrayList<>(existingErrors);
        try {
            me = accounts.getMe();
        } catch (RestClientResponseException | CallNotPermittedException e) {
            errors.add("Сервис аккаунтов временно недоступен");
            me = new Profile("", "", "", null, BigDecimal.ZERO);
        }
        return render(model, me, errors.isEmpty() ? null : errors, info);
    }

    private String render(Model model, Profile me, List<String> errors, String info) {
        String fullName = combineName(me);
        model.addAttribute("name", fullName);
        model.addAttribute("birthdate", me.dob() == null ? "" : me.dob().toString());
        model.addAttribute("sum", me.balance() == null ? BigDecimal.ZERO : me.balance());
        List<AccountDto> peers;
        try {
            peers = accounts.others();
        } catch (RestClientResponseException | CallNotPermittedException e) {
            peers = List.of();
        }
        model.addAttribute("accounts", peers);
        model.addAttribute("errors", errors);
        model.addAttribute("info", info);
        return "main";
    }

    private static String combineName(Profile me) {
        String last = me.lastName() == null ? "" : me.lastName().trim();
        String first = me.firstName() == null ? "" : me.firstName().trim();
        if (last.isEmpty() && first.isEmpty()) return "";
        if (last.isEmpty()) return first;
        if (first.isEmpty()) return last;
        return last + " " + first;
    }

    private static List<String> validateProfile(String name, LocalDate birthdate) {
        List<String> errors = new ArrayList<>();
        if (name == null || name.trim().split("\\s+").length < 2) {
            errors.add("Введите фамилию и имя через пробел");
        }
        if (birthdate == null) {
            errors.add("Введите дату рождения");
        } else if (Period.between(birthdate, LocalDate.now()).getYears() < 18) {
            errors.add("Возраст должен быть не меньше 18 лет");
        }
        return errors;
    }
}

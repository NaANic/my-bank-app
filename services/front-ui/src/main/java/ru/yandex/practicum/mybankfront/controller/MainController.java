package ru.yandex.practicum.mybankfront.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
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
        Profile me = loadProfile(model);
        renderModel(model, me, currentErrors(model), currentInfo(model));
        return "main";
    }

    @PostMapping("/account")
    public String editAccount(@RequestParam("name") String name,
                              @RequestParam("birthdate") LocalDate birthdate,
                              RedirectAttributes redirect) {
        List<String> errors = validateProfile(name, birthdate);
        if (!errors.isEmpty()) {
            redirect.addFlashAttribute("errors", errors);
            return "redirect:/account";
        }
        String[] parts = name.trim().split("\\s+", 2);
        ProfileUpdate update = new ProfileUpdate(parts[1], parts[0], birthdate);
        try {
            accounts.updateMe(update);
            redirect.addFlashAttribute("info", "Данные сохранены");
        } catch (RestClientResponseException e) {
            redirect.addFlashAttribute("errors", List.of(formatBackendError(e)));
        } catch (CallNotPermittedException e) {
            redirect.addFlashAttribute("errors", List.of("Сервис временно недоступен"));
        }
        return "redirect:/account";
    }

    @PostMapping("/cash")
    public String editCash(@RequestParam("value") int value,
                           @RequestParam("action") CashAction action,
                           RedirectAttributes redirect) {
        if (value <= 0) {
            redirect.addFlashAttribute("errors", List.of("Сумма должна быть положительной"));
            return "redirect:/account";
        }
        BigDecimal amount = BigDecimal.valueOf(value);
        try {
            switch (action) {
                case PUT -> cash.deposit(amount);
                case GET -> cash.withdraw(amount);
            }
            redirect.addFlashAttribute("info", action == CashAction.PUT
                    ? "Счёт пополнен на " + value + " руб."
                    : "Снято " + value + " руб.");
        } catch (RestClientResponseException e) {
            redirect.addFlashAttribute("errors", List.of(formatBackendError(e)));
        } catch (CallNotPermittedException e) {
            redirect.addFlashAttribute("errors", List.of("Сервис временно недоступен"));
        }
        return "redirect:/account";
    }

    @PostMapping("/transfer")
    public String transfer(@RequestParam("value") int value,
                           @RequestParam("login") String login,
                           RedirectAttributes redirect) {
        if (value <= 0) {
            redirect.addFlashAttribute("errors", List.of("Сумма перевода должна быть положительной"));
            return "redirect:/account";
        }
        if (login == null || login.isBlank()) {
            redirect.addFlashAttribute("errors", List.of("Выберите получателя"));
            return "redirect:/account";
        }
        try {
            transfer.transfer(login, BigDecimal.valueOf(value));
            redirect.addFlashAttribute("info", "Переведено " + value + " руб. пользователю " + login);
        } catch (RestClientResponseException e) {
            redirect.addFlashAttribute("errors", List.of(formatBackendError(e)));
        } catch (CallNotPermittedException e) {
            redirect.addFlashAttribute("errors", List.of("Сервис временно недоступен"));
        }
        return "redirect:/account";
    }

    private Profile loadProfile(Model model) {
        try {
            return accounts.getMe();
        } catch (RestClientResponseException | CallNotPermittedException e) {
            List<String> errors = currentErrors(model);
            errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
            errors.add("Сервис аккаунтов временно недоступен");
            model.addAttribute("errors", errors);
            return new Profile("", "", "", null, BigDecimal.ZERO);
        }
    }

    private void renderModel(Model model, Profile me, List<String> errors, String info) {
        model.addAttribute("name", combineName(me));
        model.addAttribute("birthdate", me.dob() == null ? "" : me.dob().toString());
        model.addAttribute("sum", me.balance() == null ? BigDecimal.ZERO : me.balance());
        List<AccountDto> peers;
        try {
            peers = accounts.others();
        } catch (RestClientResponseException | CallNotPermittedException e) {
            peers = List.of();
        }
        model.addAttribute("accounts", peers);
        if (!model.containsAttribute("errors")) {
            model.addAttribute("errors", errors);
        }
        if (!model.containsAttribute("info")) {
            model.addAttribute("info", info);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> currentErrors(Model model) {
        Object v = model.getAttribute("errors");
        return v instanceof List<?> list ? (List<String>) list : null;
    }

    private static String currentInfo(Model model) {
        Object v = model.getAttribute("info");
        return v instanceof String s ? s : null;
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

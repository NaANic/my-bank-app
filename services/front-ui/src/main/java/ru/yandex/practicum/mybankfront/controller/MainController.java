package ru.yandex.practicum.mybankfront.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.yandex.practicum.mybankfront.client.AccountsClient;
import ru.yandex.practicum.mybankfront.client.Profile;
import ru.yandex.practicum.mybankfront.client.ProfileUpdate;
import ru.yandex.practicum.mybankfront.controller.dto.CashAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Controller
public class MainController {

    private final AccountsClient accounts;

    public MainController(AccountsClient accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/account";
    }

    @GetMapping("/account")
    public String getAccount(Model model) {
        return render(model, accounts.getMe(), null, null);
    }

    @PostMapping("/account")
    public String editAccount(Model model,
                              @RequestParam("name") String name,
                              @RequestParam("birthdate") LocalDate birthdate) {
        List<String> errors = validateProfile(name, birthdate);
        if (!errors.isEmpty()) {
            return render(model, accounts.getMe(), errors, null);
        }
        String[] parts = name.trim().split("\\s+", 2);
        ProfileUpdate update = new ProfileUpdate(parts[1], parts[0], birthdate);
        Profile saved = accounts.updateMe(update);
        return render(model, saved, null, "Данные сохранены");
    }

    @PostMapping("/cash")
    public String editCash(Model model,
                           @RequestParam("value") int value,
                           @RequestParam("action") CashAction action) {
        return render(model, accounts.getMe(),
                List.of("Операции с наличными появятся в следующей фазе"), null);
    }

    @PostMapping("/transfer")
    public String transfer(Model model,
                           @RequestParam("value") int value,
                           @RequestParam("login") String login) {
        return render(model, accounts.getMe(),
                List.of("Переводы появятся в следующей фазе"), null);
    }

    private String render(Model model, Profile me, List<String> errors, String info) {
        String fullName = combineName(me);
        model.addAttribute("name", fullName);
        model.addAttribute("birthdate", me.dob() == null ? "" : me.dob().toString());
        model.addAttribute("sum", me.balance() == null ? BigDecimal.ZERO : me.balance());
        model.addAttribute("accounts", List.of());
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

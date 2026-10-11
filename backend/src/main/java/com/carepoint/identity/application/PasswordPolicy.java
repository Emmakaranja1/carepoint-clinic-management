package com.carepoint.identity.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Password rules evaluated per-rule so the API can report exactly which
 * requirements are unmet — the UI renders them as a live checklist.
 * Policy: ≥8 chars, uppercase, lowercase, digit. (Arbitrary composition
 * rules like "must start with a capital" are deliberately avoided — they
 * make passwords more predictable, not stronger.)
 */
@Component
public class PasswordPolicy {

    public List<String> violations(String password) {
        List<String> errors = new ArrayList<>();
        if (password == null || password.length() < 8) {
            errors.add("at least 8 characters");
        }
        if (password != null && password.length() > 72) {
            errors.add("at most 72 characters");
        }
        if (password == null || password.chars().noneMatch(Character::isUpperCase)) {
            errors.add("an uppercase letter");
        }
        if (password == null || password.chars().noneMatch(Character::isLowerCase)) {
            errors.add("a lowercase letter");
        }
        if (password == null || password.chars().noneMatch(Character::isDigit)) {
            errors.add("a digit");
        }
        return errors;
    }

    public String describe(List<String> violations) {
        return "Password must contain " + String.join(", ", violations);
    }
}

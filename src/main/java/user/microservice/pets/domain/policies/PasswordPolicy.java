package user.microservice.pets.domain.policies;

import user.microservice.pets.domain.exceptions.InvalidUserDataException;

import java.util.regex.Pattern;

public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    // Al menos: un número, una minúscula, una mayúscula y un símbolo (cualquiera)
    private static final Pattern COMPLEXITY =
            Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).+$");

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidUserDataException("Password cannot be empty");
        }
        if (password.length() < MIN_LENGTH) {
            throw new InvalidUserDataException("Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (password.length() > MAX_LENGTH) {
            throw new InvalidUserDataException("Password is too long");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidUserDataException("Password cannot contain spaces");
        }
        if (!COMPLEXITY.matcher(password).matches()) {
            throw new InvalidUserDataException(
                    "Password must contain at least one uppercase letter, one lowercase letter, " +
                            "one digit and one special character");
        }
    }

    public static boolean hasValidLength(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH;
    }
}

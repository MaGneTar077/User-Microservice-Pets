package user.microservice.pets.domain.policies;

import user.microservice.pets.domain.exceptions.InvalidUserDataException;

import java.util.regex.Pattern;

public class FullNamePolicy {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;

    // Letras Unicode (incluye tildes y ñ), espacios, guion y apostrofe. Excluye por construccion
    // caracteres de control, '<' y '>'.
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\s'-]{" + MIN_LENGTH + "," + MAX_LENGTH + "}$");

    private FullNamePolicy() {}

    /** Trim + colapsa espacios repetidos. No lanza si el valor es null o está en blanco. */
    public static String normalize(String rawFullName) {
        if (rawFullName == null) {
            return null;
        }
        String normalized = rawFullName.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    /** Valida un fullName ya normalizado. No se llama si es null (campo opcional). */
    public static void validate(String normalizedFullName) {
        if (!ALLOWED.matcher(normalizedFullName).matches()) {
            throw new InvalidUserDataException(
                    "fullName must be " + MIN_LENGTH + " to " + MAX_LENGTH
                            + " characters long and contain only letters, spaces, hyphens and apostrophes");
        }
    }
}

package user.microservice.pets.domain.policies;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import user.microservice.pets.domain.exceptions.InvalidPhoneException;

public class PhoneNumberPolicy {

    private static final String DEFAULT_REGION = "CO";

    private PhoneNumberPolicy() {}

    /**
     * Normaliza un telefono a E.164 (region por defecto CO si el numero no trae codigo de pais).
     * Devuelve null si rawPhone es null o esta en blanco (el telefono es opcional).
     * Lanza InvalidPhoneException si no es un numero valido.
     */
    public static String normalize(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber parsed = util.parse(rawPhone.trim(), DEFAULT_REGION);
            if (!util.isValidNumber(parsed)) {
                throw new InvalidPhoneException("Invalid phone number");
            }
            return util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw new InvalidPhoneException("Invalid phone number");
        }
    }
}

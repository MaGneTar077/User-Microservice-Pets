package user.microservice.pets.domain.policies;

import org.junit.jupiter.api.Test;
import user.microservice.pets.domain.exceptions.InvalidPhoneException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberPolicyTest {

    @Test
    void shouldNormalizeLocalColombianNumberToE164() {
        assertThat(PhoneNumberPolicy.normalize("3001234567")).isEqualTo("+573001234567");
    }

    @Test
    void shouldNormalizeNumberWithSpacesAndCountryCode() {
        assertThat(PhoneNumberPolicy.normalize("+57 300 123 4567")).isEqualTo("+573001234567");
    }

    @Test
    void shouldAcceptAnotherValidInternationalNumber() {
        assertThat(PhoneNumberPolicy.normalize("+14155552671")).isEqualTo("+14155552671");
    }

    @Test
    void shouldReturnNullForNullOrBlank() {
        assertThat(PhoneNumberPolicy.normalize(null)).isNull();
        assertThat(PhoneNumberPolicy.normalize("  ")).isNull();
    }

    @Test
    void shouldRejectInvalidText() {
        assertThatThrownBy(() -> PhoneNumberPolicy.normalize("not-a-phone"))
                .isInstanceOf(InvalidPhoneException.class);
    }
}

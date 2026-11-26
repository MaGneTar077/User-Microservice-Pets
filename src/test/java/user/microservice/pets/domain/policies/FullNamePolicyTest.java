package user.microservice.pets.domain.policies;

import org.junit.jupiter.api.Test;
import user.microservice.pets.domain.exceptions.InvalidUserDataException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FullNamePolicyTest {

    @Test
    void normalizeShouldTrimAndCollapseRepeatedSpaces() {
        assertThat(FullNamePolicy.normalize("  Luis   Santiago  ")).isEqualTo("Luis Santiago");
    }

    @Test
    void normalizeShouldReturnNullForNullOrBlank() {
        assertThat(FullNamePolicy.normalize(null)).isNull();
        assertThat(FullNamePolicy.normalize("   ")).isNull();
    }

    @Test
    void shouldAcceptUnicodeLettersSpacesHyphenAndApostrophe() {
        assertThat(FullNamePolicy.normalize("María José Peña-O'Connor"))
                .isEqualTo("María José Peña-O'Connor");
        FullNamePolicy.validate(FullNamePolicy.normalize("María José Peña-O'Connor"));
    }

    @Test
    void shouldRejectAngleBrackets() {
        assertThatThrownBy(() -> FullNamePolicy.validate("Luis <script>"))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void shouldRejectShorterThanTwoCharacters() {
        assertThatThrownBy(() -> FullNamePolicy.validate("L"))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void shouldRejectLongerThan100Characters() {
        assertThatThrownBy(() -> FullNamePolicy.validate("A".repeat(101)))
                .isInstanceOf(InvalidUserDataException.class);
    }
}

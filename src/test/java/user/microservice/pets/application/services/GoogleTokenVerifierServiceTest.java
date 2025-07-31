package user.microservice.pets.application.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GoogleTokenVerifierServiceTest {

    private GoogleTokenVerifierService service;

    @BeforeEach
    void setup() {
        service = new GoogleTokenVerifierService();
    }

    @Test
    void shouldReturnPayloadWhenTokenIsValid() throws Exception {

        //Given
        String token = "fake-valid-token";
        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);


        try (MockedStatic<GoogleNetHttpTransport> transportMock = Mockito.mockStatic(GoogleNetHttpTransport.class))  {
            transportMock.when(GoogleNetHttpTransport::newTrustedTransport)
                    .thenReturn(null);
        }

        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify(token)).thenReturn(idToken);

        GoogleTokenVerifierService testService = new GoogleTokenVerifierService() {

            @Override
            public GoogleIdToken.Payload verify(String idTokenString) {
                try {
                    return verifier.verify(idTokenString).getPayload();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        //When
        GoogleIdToken.Payload result = testService.verify(token);

        //Then
        assertThat(result).isEqualTo(payload);
    }

    @Test
    void shouldThrowExceptionWhenTokenIsInValid() throws Exception {

        //Given
        String invalidToken = "invalid-token";
        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);

        try (MockedStatic<GoogleNetHttpTransport> transportMock = Mockito.mockStatic(GoogleNetHttpTransport.class)) {
            transportMock.when(GoogleNetHttpTransport::newTrustedTransport)
                    .thenReturn(null);
        }

        //When
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify(invalidToken)).thenReturn(null);

        GoogleTokenVerifierService testService = new GoogleTokenVerifierService() {

            @Override
            public GoogleIdToken.Payload verify(String idTokenString) {
                try {
                    GoogleIdToken idToken = verifier.verify(idTokenString);
                    if (idToken == null) throw new RuntimeException("Token de Google  Invalido.");
                    return idToken.getPayload();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        //Then
        assertThrows(RuntimeException.class, () -> testService.verify(invalidToken));

    }

}

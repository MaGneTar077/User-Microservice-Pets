package user.microservice.pets.infrastructure.adapters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import user.microservice.pets.domain.exceptions.VeterinaryServiceUnavailableException;
import user.microservice.pets.domain.model.VeterinaryMembership;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

@DisplayName("VeterinaryMembershipAdapter - Unit Tests")
class VeterinaryMembershipAdapterTest {

    private static final String BASE_URL = "http://veterinary-service";

    private MockRestServiceServer mockServer;
    private VeterinaryMembershipAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new VeterinaryMembershipAdapter(builder.build(), BASE_URL);
    }

    @Test
    @DisplayName("Should map a 200 response to the domain model")
    void shouldMapSuccessfulResponse() {
        UUID veterinaryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        mockServer.expect(requestTo(BASE_URL + "/internal/veterinaries/" + veterinaryId + "/members/" + userId))
                .andRespond(withSuccess("""
                        {
                          "member": true,
                          "employeeId": "%s",
                          "role": "ADMIN",
                          "active": true,
                          "licensed": true,
                          "veterinaryStatus": "ACTIVE",
                          "subscriptionStatus": "ACTIVE"
                        }
                        """.formatted(employeeId), MediaType.APPLICATION_JSON));

        VeterinaryMembership membership = adapter.getMembership(veterinaryId, userId);

        assertThat(membership.member()).isTrue();
        assertThat(membership.employeeId()).isEqualTo(employeeId);
        assertThat(membership.role()).isEqualTo("ADMIN");
        assertThat(membership.isActiveMember()).isTrue();
        assertThat(membership.isLicensed()).isTrue();
        assertThat(membership.veterinaryStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Should map a 200 'not a member' response without throwing")
    void shouldMapNotMemberResponse() {
        UUID veterinaryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo(BASE_URL + "/internal/veterinaries/" + veterinaryId + "/members/" + userId))
                .andRespond(withSuccess("""
                        {"member": false, "employeeId": null, "role": null, "active": null,
                         "licensed": null, "veterinaryStatus": null, "subscriptionStatus": null}
                        """, MediaType.APPLICATION_JSON));

        VeterinaryMembership membership = adapter.getMembership(veterinaryId, userId);

        assertThat(membership.member()).isFalse();
        assertThat(membership.isActiveMember()).isFalse();
    }

    @Test
    @DisplayName("Should map a 5xx response to VeterinaryServiceUnavailableException")
    void shouldMap5xxToUnavailable() {
        UUID veterinaryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo(BASE_URL + "/internal/veterinaries/" + veterinaryId + "/members/" + userId))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.getMembership(veterinaryId, userId))
                .isInstanceOf(VeterinaryServiceUnavailableException.class);
    }

    @Test
    @DisplayName("Should map a 401 response to VeterinaryServiceUnavailableException (possible API key mismatch)")
    void shouldMap401ToUnavailable() {
        UUID veterinaryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo(BASE_URL + "/internal/veterinaries/" + veterinaryId + "/members/" + userId))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> adapter.getMembership(veterinaryId, userId))
                .isInstanceOf(VeterinaryServiceUnavailableException.class);
    }
}

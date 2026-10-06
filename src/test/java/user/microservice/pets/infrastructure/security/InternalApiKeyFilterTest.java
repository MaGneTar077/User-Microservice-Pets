package user.microservice.pets.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("InternalApiKeyFilter - Unit Tests")
class InternalApiKeyFilterTest {

    private static final String REAL_KEY = "the-real-internal-api-key";

    private InternalApiKeyFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new InternalApiKeyFilter(REAL_KEY);
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Should return 401 when the header is missing")
    void shouldRejectRequestWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/users/some-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should return 401 when the header has the wrong key")
    void shouldRejectRequestWithWrongKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/users/some-id");
        request.addHeader("X-Internal-Api-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should let the request through when the key is correct")
    void shouldAllowRequestWithCorrectKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/users/some-id");
        request.addHeader("X-Internal-Api-Key", REAL_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not touch requests outside /internal/** even without the header")
    void shouldNotFilterNonInternalPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/local");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(filterChain).doFilter(request, response);
    }
}

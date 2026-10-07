package user.microservice.pets.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class VeterinaryClientConfig {

    private static final int TIMEOUT_MS = 3000;

    @Bean
    public RestClient veterinaryRestClient(
            @Value("${veterinary.service-url}") String veterinaryServiceUrl,
            @Value("${internal.api-key}") String internalApiKey) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);

        return RestClient.builder()
                .baseUrl(veterinaryServiceUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestFactory(requestFactory)
                .build();
    }
}

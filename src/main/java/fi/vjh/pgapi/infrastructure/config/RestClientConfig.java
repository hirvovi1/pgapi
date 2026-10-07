package fi.vjh.pgapi.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${pgapi.facade.base-url:http://pg-api-facade:8091/api/v1/frontend/}")
    private String facadeBaseUrl;

    @Bean
    public RestClient pgFacadeRestClient() {
        return RestClient.builder()
                .baseUrl(facadeBaseUrl)
                .build();
    }
}

package fi.vjh.pgapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@SpringBootApplication
public class PgapiApplication {

    private static final Logger logger = LoggerFactory.getLogger(PgapiApplication.class);
    private final ObjectProvider<BuildProperties> buildProperties;

    public PgapiApplication(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @Bean
    public CorsFilter corsFilter() { // <-- Changed return type to CorsFilter
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return new CorsFilter(source); // <-- Wrap the source in the filter
    }

    public static void main(String[] args) {
        SpringApplication.run(PgapiApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationReady() {
        BuildProperties properties = buildProperties.getIfAvailable();
        if (properties == null) {
            logger.warn("------------> PG API version unavailable; build-info.properties was not generated");
            return;
        }
        logger.info("------------> PG API version {}", properties.getVersion());
    }
}

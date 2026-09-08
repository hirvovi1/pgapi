package fi.vjh.pgapi.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class JsonLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JsonLoggingFilter.class);
    private static final int MAX_LOGGED_BODY_LENGTH = 64 * 1024;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper cachedRequest =
                new ContentCachingRequestWrapper(request, MAX_LOGGED_BODY_LENGTH);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            logJsonRequest(cachedRequest);
            logJsonResponse(cachedRequest, cachedResponse);
            cachedResponse.copyBodyToResponse();
        }
    }

    private void logJsonRequest(ContentCachingRequestWrapper request) {
        if (!isJson(request.getContentType())) {
            return;
        }

        byte[] body = request.getContentAsByteArray();
        if (body.length > 0) {
            log.info("HTTP request: {} {} body={}",
                    request.getMethod(), request.getRequestURI(), bodyAsString(body));
        }
    }

    private void logJsonResponse(
            ContentCachingRequestWrapper request,
            ContentCachingResponseWrapper response) {
        if (!isJson(response.getContentType())) {
            return;
        }

        byte[] body = response.getContentAsByteArray();
        if (body.length > 0) {
            log.info("HTTP response: {} {} status={} body={}",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), bodyAsString(body));
        }
    }

    private static boolean isJson(String contentType) {
        return contentType != null
                && contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json");
    }

    private static String bodyAsString(byte[] body) {
        String value = new String(body, StandardCharsets.UTF_8);
        if (value.length() <= MAX_LOGGED_BODY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOGGED_BODY_LENGTH) + "...[truncated]";
    }
}

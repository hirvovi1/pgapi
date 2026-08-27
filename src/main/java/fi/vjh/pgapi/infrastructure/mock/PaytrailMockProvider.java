package fi.vjh.pgapi.infrastructure.mock;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class PaytrailMockProvider {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String SECRET_KEY = "SAIPPUAKAUPPIAS";
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/callbacks/paytrail";

    public String initiatePayment(UUID transactionId, long amountCents) {
        // 1. Simuloidaan maksusivun URL-osoitetta
        String mockCheckoutUrl = "https://paytrail.mock" + UUID.randomUUID();

        // 2. Ajastetaan asynkroninen callback (simuloi asiakkaan maksutapahtumaa)
        scheduler.schedule(() -> triggerWebhook(transactionId, amountCents), 3, TimeUnit.SECONDS);

        return mockCheckoutUrl;
    }

    private void triggerWebhook(UUID transactionId, long amountCents) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("transactionId", transactionId.toString());
            body.put("status", "OK");
            body.put("amountCents", amountCents); // Lähetetään puhtaana long-lukuna

            String jsonBody = new ObjectMapper().writeValueAsString(body);
            String signature = calculateHmac(jsonBody, SECRET_KEY);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Paytrail-Signature", signature);
            headers.set("Idempotency-Key", transactionId.toString());

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            restTemplate.postForEntity(CALLBACK_URL, entity, String.class);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String calculateHmac(String data, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

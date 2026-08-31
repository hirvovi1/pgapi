package fi.vjh.pgapi;

import fi.vjh.pgapi.infrastructure.security.SecurityUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @Test
    void shouldGenerateStableSignatureForSamePayload() throws Exception {
        String payload = "{\"transactionId\":\"abc-123\",\"status\":\"OK\",\"amountCents\":5000}";

        String first = SecurityUtils.calculateHmac(payload, SecurityUtils.SECRET);
        String second = SecurityUtils.calculateHmac(payload, SecurityUtils.SECRET);

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotBlank();
    }

    @Test
    void shouldAcceptSignedPayloadWithPaytrailHeaderFormat() throws Exception {
        String payload = "{\"transactionId\":\"abc-123\",\"status\":\"OK\",\"amountCents\":5000}";
        String signature = "sha256=" + SecurityUtils.calculateHmac(payload, SecurityUtils.SECRET);

        assertThat(SecurityUtils.isValidSignature(payload, signature, SecurityUtils.SECRET)).isTrue();
    }

    @Test
    void shouldRejectTamperedPayload() throws Exception {
        String originalPayload = "{\"transactionId\":\"abc-123\",\"status\":\"OK\",\"amountCents\":5000}";
        String tamperedPayload = "{\"transactionId\":\"abc-123\",\"status\":\"FAILED\",\"amountCents\":5000}";
        String signature = SecurityUtils.calculateHmac(originalPayload, SecurityUtils.SECRET);

        assertThat(SecurityUtils.isValidSignature(tamperedPayload, signature, SecurityUtils.SECRET)).isFalse();
    }
}

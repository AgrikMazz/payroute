package dev.payroute;

import dev.payroute.security.WebhookSignatures;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignaturesTest {
    @Test
    void validatesExactBodyAndRejectsModifiedOrMalformedSignatures() {
        String body = "{\"eventId\":\"e1\",\"eventType\":\"payment.succeeded\"}";
        String signature = WebhookSignatures.hmacSha256("secret", body);
        assertThat(WebhookSignatures.verify("secret", body, signature)).isTrue();
        assertThat(WebhookSignatures.verify("secret", body + " ", signature)).isFalse();
        assertThat(WebhookSignatures.verify("secret", body, "not-hex")).isFalse();
    }
}

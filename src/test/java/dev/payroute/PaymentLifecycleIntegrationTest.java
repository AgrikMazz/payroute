package dev.payroute;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.payroute.api.CreatePaymentRequest;
import dev.payroute.api.CreateRefundRequest;
import dev.payroute.api.SettlementReportRequest;
import dev.payroute.domain.PaymentMethod;
import dev.payroute.domain.SimulationScenario;
import dev.payroute.repository.LedgerEntryRepository;
import dev.payroute.service.PaymentService;
import dev.payroute.service.ProviderWebhookService;
import dev.payroute.service.ReconciliationService;
import dev.payroute.service.RefundService;
import dev.payroute.security.WebhookSignatures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "payroute.webhooks.dispatcher-enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class PaymentLifecycleIntegrationTest {
    private static final String WEBHOOK_SECRET = "local-webhook-secret-change-me";
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("payroute_test").withUsername("payroute").withPassword("test-pass");

    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("payroute.security.webhook-secret", () -> WEBHOOK_SECRET);
    }

    @Autowired PaymentService payments;
    @Autowired RefundService refunds;
    @Autowired ProviderWebhookService webhooks;
    @Autowired ReconciliationService reconciliation;
    @Autowired LedgerEntryRepository ledger;
    @Autowired ObjectMapper mapper;

    @Test
    void paymentReplayAndChangedPayloadAreHandledIdempotently() {
        var request = new CreatePaymentRequest("merchant-test-1", 12_345, "INR", PaymentMethod.UPI,
                List.of(), SimulationScenario.SUCCESS);
        var created = payments.create(request, "payment-key-1");
        var replay = payments.create(request, "payment-key-1");
        assertThat(created.id()).isEqualTo(replay.id());
        assertThat(replay.idempotentReplay()).isTrue();
        assertThat(created.status()).isEqualTo("SUCCEEDED");
        assertThatThrownBy(() -> payments.create(new CreatePaymentRequest("merchant-test-1", 12_346, "INR",
                PaymentMethod.UPI, List.of(), SimulationScenario.SUCCESS), "payment-key-1"))
                .hasMessageContaining("different request payload");
    }

    @Test
    void transientProviderFailureRetriesAndRefundLedgerRemainsBalanced() {
        var request = new CreatePaymentRequest("merchant-test-2", 25_000, "INR", PaymentMethod.CARD,
                List.of(), SimulationScenario.TRANSIENT_FAILURE);
        var payment = payments.create(request, "payment-key-2");
        assertThat(payment.status()).isEqualTo("SUCCEEDED");
        assertThat(payment.attempts()).hasSize(2);
        var refund = refunds.refund(payment.id(), "refund-key-1", new CreateRefundRequest(5_000L));
        assertThat(refund.status()).isEqualTo("SUCCEEDED");
        assertThat(refunds.refund(payment.id(), "refund-key-1", new CreateRefundRequest(5_000L)).id()).isEqualTo(refund.id());
        var entries = ledger.findAllByPaymentIdOrderByCreatedAtAsc(payment.id());
        assertThat(entries).isNotEmpty();
        for (var transferId : entries.stream().map(e -> e.transferId).distinct().toList()) {
            var transfer = ledger.findAllByTransferId(transferId);
            long debit = transfer.stream().filter(e -> e.direction.equals("DEBIT")).mapToLong(e -> e.amountMinor).sum();
            long credit = transfer.stream().filter(e -> e.direction.equals("CREDIT")).mapToLong(e -> e.amountMinor).sum();
            assertThat(debit).isEqualTo(credit);
        }
    }

    @Test
    void signedWebhookCompletesTimedOutPaymentAndDuplicateEventIsIgnored() throws Exception {
        var request = new CreatePaymentRequest("merchant-test-3", 8_500, "INR", PaymentMethod.UPI,
                List.of(), SimulationScenario.TIMEOUT);
        var payment = payments.create(request, "payment-key-3");
        assertThat(payment.status()).isEqualTo("PROCESSING");
        String body = mapper.writeValueAsString(Map.of("eventId", "event-test-3", "providerReference", payment.providerReference(), "eventType", "payment.succeeded"));
        String signature = WebhookSignatures.hmacSha256(WEBHOOK_SECRET, body);
        assertThat(webhooks.accept(payment.providerCode(), signature, body).outcome()).isEqualTo("APPLIED");
        assertThat(webhooks.accept(payment.providerCode(), signature, body).outcome()).isEqualTo("DUPLICATE");
        var resolved = payments.get(payment.id());
        assertThat(resolved.attempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.outcome()).isEqualTo("TIMEOUT");
            assertThat(attempt.resolvedOutcome()).isEqualTo("CAPTURED");
        });
    }

    @Test
    void reconciliationReportsSettlementFeeDiscrepancies() {
        var payment = payments.create(new CreatePaymentRequest("merchant-test-4", 10_000, "INR", PaymentMethod.CARD,
                List.of(), SimulationScenario.SUCCESS), "payment-key-4");
        var result = reconciliation.run(new SettlementReportRequest(payment.providerCode(), List.of(
                new SettlementReportRequest.Row(payment.providerReference(), payment.amountMinor(), payment.providerFeeMinor() + 1))));
        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.providerReference()).isEqualTo(payment.providerReference());
            assertThat(item.discrepancyType()).isEqualTo("FEE_MISMATCH");
        });
    }

    @Test
    void matchedSettlementPostsBalancedLedgerEntriesOnlyOnce() {
        var payment = payments.create(new CreatePaymentRequest("merchant-test-5", 15_000, "INR", PaymentMethod.UPI,
                List.of(), SimulationScenario.SUCCESS), "payment-key-5");
        var report = new SettlementReportRequest(payment.providerCode(), List.of(
                new SettlementReportRequest.Row(payment.providerReference(), payment.amountMinor(), payment.providerFeeMinor())));
        reconciliation.run(report);
        reconciliation.run(report);

        var settlementEntries = ledger.findAllByPaymentIdOrderByCreatedAtAsc(payment.id()).stream()
                .filter(entry -> entry.entryType.equals("SETTLEMENT")).toList();
        assertThat(settlementEntries).hasSize(2);
        assertThat(settlementEntries.stream().map(e -> e.transferId).distinct()).hasSize(1);
    }
}

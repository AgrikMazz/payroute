package dev.payroute;

import dev.payroute.domain.LedgerEntry;
import dev.payroute.domain.Payment;
import dev.payroute.repository.LedgerEntryRepository;
import dev.payroute.service.LedgerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LedgerServiceTest {
    @Test
    void captureAndRefundPostBalancedEntriesInPaise() {
        var repository = mock(LedgerEntryRepository.class);
        var service = new LedgerService(repository, Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC));
        Payment payment = new Payment(); payment.id = UUID.randomUUID(); payment.merchantId = "merchant-1";
        payment.amountMinor = 12_500; payment.currency = "INR"; payment.providerFeeMinor = 25; payment.merchantFeeMinor = 0;

        service.postCapture(payment);
        service.postRefund(payment, 2_500);

        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(3)).saveAll(captor.capture());
        for (var transfer : captor.getAllValues()) {
            long debits = transfer.stream().filter(e -> e.direction.equals("DEBIT")).mapToLong(e -> e.amountMinor).sum();
            long credits = transfer.stream().filter(e -> e.direction.equals("CREDIT")).mapToLong(e -> e.amountMinor).sum();
            assertThat(debits).isEqualTo(credits);
            assertThat(transfer).allSatisfy(e -> assertThat(e.amountMinor).isPositive());
        }
        assertThat(captor.getAllValues().get(0)).hasSize(3);
        assertThat(captor.getAllValues().get(1)).hasSize(2);
        assertThat(captor.getAllValues().get(2)).hasSize(2);
    }
}

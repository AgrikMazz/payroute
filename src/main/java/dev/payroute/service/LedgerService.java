package dev.payroute.service;

import dev.payroute.api.ApiException;
import dev.payroute.domain.LedgerEntry;
import dev.payroute.domain.Payment;
import dev.payroute.repository.LedgerEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {
    private final LedgerEntryRepository entries;
    private final Clock clock;
    public LedgerService(LedgerEntryRepository entries, Clock clock) { this.entries = entries; this.clock = clock; }

    public void postCapture(Payment p) {
        UUID transfer = UUID.randomUUID();
        List<LedgerEntry> pair = new ArrayList<>();
        long clearing = p.amountMinor - p.providerFeeMinor;
        long payable = p.amountMinor - p.merchantFeeMinor;
        add(pair, transfer, p, "processor_clearing", "DEBIT", clearing, "PAYMENT_CAPTURE");
        add(pair, transfer, p, "provider_fee_expense", "DEBIT", p.providerFeeMinor, "PAYMENT_CAPTURE");
        add(pair, transfer, p, "merchant_payable:" + p.merchantId, "CREDIT", payable, "PAYMENT_CAPTURE");
        add(pair, transfer, p, "platform_fee_revenue", "CREDIT", p.merchantFeeMinor, "PAYMENT_CAPTURE");
        postBalanced(pair);
    }

    public void postRefund(Payment p, long amountMinor) {
        UUID liabilityTransfer = UUID.randomUUID();
        List<LedgerEntry> liability = new ArrayList<>();
        add(liability, liabilityTransfer, p, "merchant_payable:" + p.merchantId, "DEBIT", amountMinor, "REFUND");
        add(liability, liabilityTransfer, p, "refunds_payable", "CREDIT", amountMinor, "REFUND");
        postBalanced(liability);

        UUID payoutTransfer = UUID.randomUUID();
        List<LedgerEntry> payout = new ArrayList<>();
        add(payout, payoutTransfer, p, "refunds_payable", "DEBIT", amountMinor, "REFUND_PAYOUT");
        add(payout, payoutTransfer, p, "bank_cash", "CREDIT", amountMinor, "REFUND_PAYOUT");
        postBalanced(payout);
    }

    public void postSettlement(Payment p, long grossAmountMinor, long providerFeeMinor) {
        long net = grossAmountMinor - providerFeeMinor;
        if (net < 0) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Provider fee exceeds the reported settlement amount.");
        UUID transfer = UUID.randomUUID();
        List<LedgerEntry> settlement = new ArrayList<>();
        add(settlement, transfer, p, "bank_cash", "DEBIT", net, "SETTLEMENT");
        add(settlement, transfer, p, "processor_clearing", "CREDIT", net, "SETTLEMENT");
        postBalanced(settlement);
    }

    public List<LedgerEntry> forPayment(UUID paymentId) { return entries.findAllByPaymentIdOrderByCreatedAtAsc(paymentId); }

    private void add(List<LedgerEntry> out, UUID transfer, Payment p, String account, String direction, long amount, String type) {
        if (amount <= 0) return;
        LedgerEntry e = new LedgerEntry(); e.id = UUID.randomUUID(); e.transferId = transfer; e.paymentId = p.id;
        e.accountCode = account; e.direction = direction; e.amountMinor = amount; e.currency = p.currency;
        e.entryType = type; e.createdAt = clock.instant(); out.add(e);
    }
    private void postBalanced(List<LedgerEntry> entries) {
        long debits = entries.stream().filter(e -> e.direction.equals("DEBIT")).mapToLong(e -> e.amountMinor).sum();
        long credits = entries.stream().filter(e -> e.direction.equals("CREDIT")).mapToLong(e -> e.amountMinor).sum();
        if (debits != credits) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unbalanced ledger transfer was rejected.");
        this.entries.saveAll(entries);
    }
}

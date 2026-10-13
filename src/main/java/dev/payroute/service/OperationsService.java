package dev.payroute.service;

import dev.payroute.api.*;
import dev.payroute.domain.FeeRule;
import dev.payroute.domain.ProviderConfig;
import dev.payroute.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class OperationsService {
    private final ProviderConfigRepository providers;
    private final FeeRuleRepository rules;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final ReconciliationItemRepository discrepancies;
    private final WebhookDeliveryRepository deliveries;
    private final LedgerEntryRepository ledger;
    private final SettlementRecordRepository settlementRecords;
    private final Clock clock;
    public OperationsService(ProviderConfigRepository providers, FeeRuleRepository rules, PaymentRepository payments,
                             PaymentAttemptRepository attempts, ReconciliationItemRepository discrepancies,
                             WebhookDeliveryRepository deliveries, LedgerEntryRepository ledger,
                             SettlementRecordRepository settlementRecords, Clock clock) {
        this.providers = providers; this.rules = rules; this.payments = payments; this.attempts = attempts;
        this.discrepancies = discrepancies; this.deliveries = deliveries; this.ledger = ledger;
        this.settlementRecords = settlementRecords; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<ProviderView> providers() { return providers.findAll().stream().map(ProviderView::of).toList(); }
    @Transactional
    public ProviderView updateProvider(String code, ProviderConfigRequest request) {
        ProviderConfig p = providers.findById(code).orElseThrow(() -> ApiException.notFound("Provider was not found."));
        p.enabled = request.enabled(); p.providerFeeBps = request.providerFeeBps(); p.fixedFeeMinor = request.fixedFeeMinor();
        return ProviderView.of(providers.save(p));
    }
    @Transactional(readOnly = true)
    public List<FeeRuleView> feeRules() { return rules.findAll().stream().map(FeeRuleView::of).toList(); }
    @Transactional
    public FeeRuleView createFeeRule(FeeRuleRequest request) {
        FeeRule r = new FeeRule(); r.id = UUID.randomUUID(); r.ruleName = request.ruleName(); r.merchantId = request.merchantId();
        r.paymentMethod = request.paymentMethod(); r.rateBps = request.rateBps(); r.fixedFeeMinor = request.fixedFeeMinor();
        r.activeFrom = request.activeFrom() == null ? clock.instant() : request.activeFrom(); r.activeUntil = request.activeUntil();
        r.enabled = request.enabled() == null || request.enabled();
        if (r.activeUntil != null && !r.activeUntil.isAfter(r.activeFrom)) throw ApiException.badRequest("activeUntil must be after activeFrom.");
        return FeeRuleView.of(rules.save(r));
    }
    @Transactional(readOnly = true)
    public List<LedgerEntryView> ledger(UUID paymentId) {
        if (!payments.existsById(paymentId)) throw ApiException.notFound("Payment was not found.");
        return ledger.findAllByPaymentIdOrderByCreatedAtAsc(paymentId).stream().map(LedgerEntryView::of).toList();
    }
    @Transactional(readOnly = true)
    public List<SettlementRecordView> settlements() {
        return settlementRecords.findAllByOrderByImportedAtDesc().stream().map(SettlementRecordView::of).toList();
    }
    @Transactional(readOnly = true)
    public AnalyticsOverview overview() {
        var allPayments = payments.findAll();
        long completed = allPayments.stream().filter(p -> List.of("SUCCEEDED", "FAILED", "PARTIALLY_REFUNDED", "REFUNDED").contains(p.status.name())).count();
        long succeeded = allPayments.stream().filter(p -> List.of("SUCCEEDED", "PARTIALLY_REFUNDED", "REFUNDED").contains(p.status.name())).count();
        long processing = allPayments.stream().filter(p -> List.of("PENDING", "PROCESSING").contains(p.status.name())).count();
        long totalProviderFees = allPayments.stream().filter(p -> List.of("SUCCEEDED", "PARTIALLY_REFUNDED", "REFUNDED").contains(p.status.name())).mapToLong(p -> p.providerFeeMinor).sum();
        long savings = allPayments.stream().filter(p -> List.of("SUCCEEDED", "PARTIALLY_REFUNDED", "REFUNDED").contains(p.status.name())).mapToLong(p -> p.avoidedProviderFeeMinor).sum();
        List<ProviderPerformanceView> performance = providers.findAll().stream().map(p -> {
            var list = attempts.findAllByProviderCode(p.code);
            int successes = (int) list.stream().filter(a -> (a.resolvedOutcome == null ? a.outcome : a.resolvedOutcome).equals("CAPTURED")).count();
            long evaluated = list.stream().filter(a -> !a.outcome.equals("TIMEOUT") || a.resolvedOutcome != null).count();
            return new ProviderPerformanceView(p.code, list.size(), successes,
                    evaluated == 0 ? p.successRate : (double) successes / evaluated,
                    list.isEmpty() ? p.p95LatencyMs : list.stream().mapToLong(a -> a.latencyMs).average().orElse(0));
        }).toList();
        long outstanding = deliveries.countByStatusIn(List.of("PENDING", "RETRYING", "DEAD"));
        return new AnalyticsOverview(allPayments.size(), completed, completed == 0 ? 0 : (double) succeeded / completed,
                processing, totalProviderFees, savings, discrepancies.countByResolvedFalse(), outstanding, performance);
    }
}

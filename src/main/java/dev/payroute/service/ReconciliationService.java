package dev.payroute.service;

import dev.payroute.api.ApiException;
import dev.payroute.api.ReconciliationResponse;
import dev.payroute.api.SettlementReportRequest;
import dev.payroute.domain.*;
import dev.payroute.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ReconciliationService {
    private final ProviderConfigRepository providers;
    private final PaymentRepository payments;
    private final ReconciliationRunRepository runs;
    private final ReconciliationItemRepository items;
    private final SettlementRecordRepository settlementRecords;
    private final LedgerService ledger;
    private final MeterRegistry meters;
    private final Clock clock;
    public ReconciliationService(ProviderConfigRepository providers, PaymentRepository payments, ReconciliationRunRepository runs,
                                 ReconciliationItemRepository items, SettlementRecordRepository settlementRecords,
                                 LedgerService ledger, MeterRegistry meters, Clock clock) {
        this.providers = providers; this.payments = payments; this.runs = runs; this.items = items;
        this.settlementRecords = settlementRecords; this.ledger = ledger; this.meters = meters; this.clock = clock;
    }

    @Transactional
    public ReconciliationResponse run(SettlementReportRequest report) {
        if (!providers.existsById(report.providerCode())) throw ApiException.notFound("Provider was not found.");
        Set<String> references = new HashSet<>();
        for (var row : report.rows()) {
            if (!references.add(row.providerReference()))
                throw ApiException.badRequest("Settlement report contains a duplicate providerReference: " + row.providerReference());
            if (row.feeMinor() > row.amountMinor()) throw ApiException.badRequest("Provider fee cannot exceed the reported gross amount.");
        }
        ReconciliationRun run = new ReconciliationRun(); run.id = UUID.randomUUID(); run.providerCode = report.providerCode();
        run.status = "RUNNING"; run.createdAt = clock.instant(); run.rowCount = report.rows().size(); runs.save(run);
        int matched = 0, discrepancy = 0;
        for (var row : report.rows()) {
            Payment p = payments.findByProviderReference(row.providerReference()).orElse(null);
            if (p == null) {
                record(report.providerCode(), row, null, "ORPHAN_PROVIDER_TRANSACTION", false);
                add(run, row.providerReference(), null, null, row.amountMinor(), null, row.feeMinor(), "ORPHAN_PROVIDER_TRANSACTION");
                discrepancy++; continue;
            }
            if (!p.selectedProviderCode.equals(report.providerCode())) {
                record(report.providerCode(), row, p, "PROVIDER_MISMATCH", false);
                add(run, row.providerReference(), p.id, p.amountMinor, row.amountMinor(), p.providerFeeMinor, row.feeMinor(), "PROVIDER_MISMATCH");
                discrepancy++; continue;
            }
            if (p.status != PaymentStatus.SUCCEEDED && p.status != PaymentStatus.PARTIALLY_REFUNDED && p.status != PaymentStatus.REFUNDED) {
                record(report.providerCode(), row, p, "STATUS_MISMATCH", false);
                add(run, row.providerReference(), p.id, p.amountMinor, row.amountMinor(), p.providerFeeMinor, row.feeMinor(), "STATUS_MISMATCH");
                discrepancy++; continue;
            }
            if (p.amountMinor != row.amountMinor()) {
                record(report.providerCode(), row, p, "AMOUNT_MISMATCH", false);
                add(run, row.providerReference(), p.id, p.amountMinor, row.amountMinor(), p.providerFeeMinor, row.feeMinor(), "AMOUNT_MISMATCH");
                discrepancy++; continue;
            }
            if (p.providerFeeMinor != row.feeMinor()) {
                record(report.providerCode(), row, p, "FEE_MISMATCH", false);
                add(run, row.providerReference(), p.id, p.amountMinor, row.amountMinor(), p.providerFeeMinor, row.feeMinor(), "FEE_MISMATCH");
                discrepancy++; continue;
            }
            record(report.providerCode(), row, p, "MATCHED", true);
            add(run, row.providerReference(), p.id, p.amountMinor, row.amountMinor(), p.providerFeeMinor, row.feeMinor(), "MATCHED");
            matched++;
        }
        List<Payment> internal = payments.findAllByStatusIn(List.of(PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED));
        for (Payment p : internal) {
            if (report.providerCode().equals(p.selectedProviderCode) && !references.contains(p.providerReference)) {
                add(run, p.providerReference, p.id, p.amountMinor, null, p.providerFeeMinor, null, "MISSING_IN_PROVIDER_REPORT");
                discrepancy++;
            }
        }
        run.matchedCount = matched; run.discrepancyCount = discrepancy;
        run.status = discrepancy == 0 ? "CLEAN" : "HAS_DISCREPANCIES";
        runs.save(run); meters.counter("payroute.reconciliation.runs", "status", run.status).increment();
        return response(run);
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse get(UUID id) {
        ReconciliationRun run = runs.findById(id).orElseThrow(() -> ApiException.notFound("Reconciliation run was not found."));
        return response(run);
    }
    private ReconciliationResponse response(ReconciliationRun r) {
        return ReconciliationResponse.of(r, items.findAllByRunIdOrderByCreatedAtAsc(r.id));
    }
    private void add(ReconciliationRun run, String reference, UUID paymentId, Long expectedAmount, Long reportedAmount,
                     Long expectedFee, Long reportedFee, String type) {
        ReconciliationItem i = new ReconciliationItem(); i.id = UUID.randomUUID(); i.runId = run.id;
        i.providerReference = reference; i.paymentId = paymentId; i.expectedAmountMinor = expectedAmount;
        i.reportedAmountMinor = reportedAmount; i.expectedFeeMinor = expectedFee; i.reportedFeeMinor = reportedFee;
        i.discrepancyType = type; i.resolved = type.equals("MATCHED"); i.createdAt = clock.instant(); items.save(i);
    }
    private void record(String providerCode, SettlementReportRequest.Row row, Payment payment, String status, boolean matched) {
        SettlementRecord record = settlementRecords.findByProviderCodeAndProviderReference(providerCode, row.providerReference())
                .orElseGet(() -> { SettlementRecord created = new SettlementRecord(); created.id = UUID.randomUUID();
                    created.providerCode = providerCode; created.providerReference = row.providerReference(); return created; });
        record.paymentId = payment == null ? null : payment.id;
        record.reportedAmountMinor = row.amountMinor(); record.reportedFeeMinor = row.feeMinor();
        record.status = status; record.importedAt = clock.instant();
        if (matched && payment != null && !record.ledgerPosted) {
            ledger.postSettlement(payment, row.amountMinor(), row.feeMinor());
            record.ledgerPosted = true;
        }
        settlementRecords.save(record);
    }
}

package dev.payroute.api;

import dev.payroute.service.OperationsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class OperationsController {
    private final OperationsService operations;
    public OperationsController(OperationsService operations) { this.operations = operations; }
    @GetMapping("/providers") public List<ProviderView> providers() { return operations.providers(); }
    @PutMapping("/providers/{code}/config") public ProviderView updateProvider(@PathVariable String code, @Valid @RequestBody ProviderConfigRequest request) { return operations.updateProvider(code, request); }
    @GetMapping("/fee-rules") public List<FeeRuleView> feeRules() { return operations.feeRules(); }
    @GetMapping("/settlements") public List<SettlementRecordView> settlements() { return operations.settlements(); }
    @PostMapping("/fee-rules") public FeeRuleView createFeeRule(@Valid @RequestBody FeeRuleRequest request) { return operations.createFeeRule(request); }
    @GetMapping("/analytics/overview") public AnalyticsOverview overview() { return operations.overview(); }
    @GetMapping("/payments/{paymentId}/ledger") public List<LedgerEntryView> ledger(@PathVariable java.util.UUID paymentId) { return operations.ledger(paymentId); }
}

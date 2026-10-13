package dev.payroute.api;

import dev.payroute.service.ReconciliationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {
    private final ReconciliationService reconciliation;
    public ReconciliationController(ReconciliationService reconciliation) { this.reconciliation = reconciliation; }
    @PostMapping("/runs") public ReconciliationResponse run(@Valid @RequestBody SettlementReportRequest request) { return reconciliation.run(request); }
    @GetMapping("/runs/{id}") public ReconciliationResponse get(@PathVariable UUID id) { return reconciliation.get(id); }
}

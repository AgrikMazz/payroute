package dev.payroute.api;

import dev.payroute.service.PaymentService;
import dev.payroute.service.RefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
public class PaymentController {
    private final PaymentService payments;
    private final RefundService refunds;
    public PaymentController(PaymentService payments, RefundService refunds) { this.payments = payments; this.refunds = refunds; }

    @PostMapping
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request,
                                  @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return payments.create(request, key);
    }
    @GetMapping("/{id}") public PaymentResponse get(@PathVariable UUID id) { return payments.get(id); }
    @PostMapping("/{id}/refunds")
    public RefundResponse refund(@PathVariable UUID id, @Valid @RequestBody CreateRefundRequest request,
                                 @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return refunds.refund(id, key, request);
    }
}

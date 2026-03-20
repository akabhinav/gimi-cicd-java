package dev.gimi.core.model.saas;

import java.time.Instant;
import java.util.List;

/**
 * Billing invoice for a SaaS tenant.
 */
public record SaasInvoice(
        String id,
        String tenantId,
        String billingPeriod,
        SaasPlan plan,
        double planBaseCost,
        double overageCost,
        double totalCost,
        String currency,
        InvoiceStatus status,
        List<LineItem> lineItems,
        Instant issuedAt,
        Instant dueAt,
        Instant paidAt
) {
    public SaasInvoice {
        currency = currency == null ? "USD" : currency;
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        status = status == null ? InvoiceStatus.PENDING : status;
    }

    public record LineItem(
            String description,
            int quantity,
            String unit,
            double unitPrice,
            double totalPrice
    ) {}

    public enum InvoiceStatus {
        PENDING,
        ISSUED,
        PAID,
        OVERDUE,
        CANCELLED
    }
}

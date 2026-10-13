package dev.payroute.api;

import java.util.List;

public record AnalyticsOverview(long paymentCount, long completedCount, double successRate,
                                long processingCount, long totalProviderFeeMinor,
                                long estimatedSavingsVsHighestCostProviderMinor,
                                long unresolvedReconciliationCount, long outstandingWebhookDeliveryCount,
                                List<ProviderPerformanceView> providers) {}

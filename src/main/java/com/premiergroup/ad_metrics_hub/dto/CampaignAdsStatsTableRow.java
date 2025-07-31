package com.premiergroup.ad_metrics_hub.dto;

import java.math.BigDecimal;

public record CampaignAdsStatsTableRow(
        String campaignName,
        String status,
        MetricStats<Integer> impressions,
        MetricStats<Integer> clicks,
        MetricStats<Integer> conversions,
        MetricStats<BigDecimal> cost,
        MetricStats<BigDecimal> costPerConversion,
        MetricStats<BigDecimal> conversionRate) {
}

package com.premiergroup.ad_metrics_hub.dto;

import java.math.BigDecimal;
import java.util.Collection;

public record WidgetAdsStats(
        Collection<Integer> impressions,
        Collection<Integer> clicks,
        Collection<Integer> conversions,
        Collection<BigDecimal> cost) {
}

package com.premiergroup.ad_metrics_hub.dto;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public record MetricStats<T extends Number>(
        List<String> labels,
        Collection<T> values,
        T total,
        BigDecimal percentChange
) {}

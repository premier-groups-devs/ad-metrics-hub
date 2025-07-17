package com.premiergroup.ad_metrics_hub.service;

import com.premiergroup.ad_metrics_hub.dto.MetricStats;
import com.premiergroup.ad_metrics_hub.dto.WidgetAdsStats;
import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import com.premiergroup.ad_metrics_hub.enums.DateFilter;
import com.premiergroup.ad_metrics_hub.repository.CampaignMetricRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
@Log4j2
@AllArgsConstructor
public class AdStatsService {

    private final CampaignMetricRepository campaignMetricRepository;

    public WidgetAdsStats getStatsByMarketingChannelIdAndDateRange(
            Integer marketingChannelId,
            DateFilter dateRange
    ) {
        LocalDate start = dateRange.getStartDate();
        LocalDate end = dateRange.getEndDate();

        //–– fetch current and sum by day ––
        List<CampaignMetric> currentMetrics = campaignMetricRepository
                .findByCampaign_MarketingChannel_IdAndStatsDateBetween(marketingChannelId, start, end);

        Map<LocalDate, Integer> sumImpr = new TreeMap<>();
        Map<LocalDate, Integer> sumClicks = new TreeMap<>();
        Map<LocalDate, Integer> sumConv = new TreeMap<>();
        Map<LocalDate, BigDecimal> sumCost = new TreeMap<>();

        for (var cm : currentMetrics) {
            LocalDate d = cm.getStatsDate();
            sumImpr.merge(d, cm.getImpressions(), Integer::sum);
            sumClicks.merge(d, cm.getClicks(), Integer::sum);
            sumConv.merge(d, cm.getConversions(), Integer::sum);
            sumCost.merge(d, cm.getCost(), BigDecimal::add);
        }

        //–– build labels, totals and % changes ––
        MetricStats<Integer> imprStats = buildIntStats(sumImpr, start, end, marketingChannelId, DateFilterType.IMPRESSIONS);
        MetricStats<Integer> clicksStats = buildIntStats(sumClicks, start, end, marketingChannelId, DateFilterType.CLICKS);
        MetricStats<Integer> convStats = buildIntStats(sumConv, start, end, marketingChannelId, DateFilterType.CONVERSIONS);
        MetricStats<BigDecimal> costStats = buildDecStats(sumCost, start, end, marketingChannelId);

        return new WidgetAdsStats(imprStats, clicksStats, convStats, costStats);
    }

    // helper for Integer metrics
    private MetricStats<Integer> buildIntStats(
            Map<LocalDate, Integer> series,
            LocalDate start, LocalDate end,
            Integer channelId, DateFilterType type
    ) {
        List<String> labels = series.keySet().stream().map(LocalDate::toString).toList();
        int total = series.values().stream().mapToInt(i -> i).sum();

        // previous period sum
        long days = DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(days - 1);
        List<CampaignMetric> prevMetrics = campaignMetricRepository
                .findByCampaign_MarketingChannel_IdAndStatsDateBetween(channelId, prevStart, prevEnd);

        int prevTotal = switch (type) {
            case IMPRESSIONS -> prevMetrics.stream().mapToInt(CampaignMetric::getImpressions).sum();
            case CLICKS -> prevMetrics.stream().mapToInt(CampaignMetric::getClicks).sum();
            case CONVERSIONS -> prevMetrics.stream().mapToInt(CampaignMetric::getConversions).sum();
        };

        BigDecimal pct = percentChange(BigDecimal.valueOf(total), BigDecimal.valueOf(prevTotal));
        return new MetricStats<>(labels, series.values(), total, pct);
    }

    // helper for BigDecimal “cost”
    private MetricStats<BigDecimal> buildDecStats(
            Map<LocalDate, BigDecimal> series,
            LocalDate start, LocalDate end,
            Integer channelId
    ) {
        List<String> labels = series.keySet().stream().map(LocalDate::toString).toList();
        BigDecimal total = series.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long days = DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(days - 1);
        List<CampaignMetric> prevMetrics = campaignMetricRepository
                .findByCampaign_MarketingChannel_IdAndStatsDateBetween(channelId, prevStart, prevEnd);

        BigDecimal prevTotal = prevMetrics.stream()
                .map(CampaignMetric::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pct = percentChange(total, prevTotal);
        return new MetricStats<>(labels, series.values(), total, pct);
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(100);
        }
        return current
                .subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    // simple enum to pick which int-metric we’re doing
    private enum DateFilterType {IMPRESSIONS, CLICKS, CONVERSIONS}

}

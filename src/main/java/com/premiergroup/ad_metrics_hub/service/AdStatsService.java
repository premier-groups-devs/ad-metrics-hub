package com.premiergroup.ad_metrics_hub.service;

import com.premiergroup.ad_metrics_hub.dto.CampaignAdsStatsGraph;
import com.premiergroup.ad_metrics_hub.dto.CampaignAdsStatsTableRow;
import com.premiergroup.ad_metrics_hub.dto.MetricStats;
import com.premiergroup.ad_metrics_hub.dto.WidgetAdsStats;
import com.premiergroup.ad_metrics_hub.entity.Campaign;
import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import com.premiergroup.ad_metrics_hub.enums.DateFilter;
import com.premiergroup.ad_metrics_hub.enums.MetricFilter;
import com.premiergroup.ad_metrics_hub.repository.CampaignMetricRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
@Log4j2
@AllArgsConstructor
public class AdStatsService {

    private CampaignMetricRepository campaignMetricRepository;

    public WidgetAdsStats getSWidgetAdsStats(
            Integer marketingChannelId,
            DateFilter dateRange,
            String startDate,
            String endDate
    ) {
        LocalDate start = dateRange.getStartDate();
        LocalDate end = dateRange.getEndDate();
        boolean isMonthlyGroup = "MONTH".equals(dateRange.getType());

        if (dateRange.equals(DateFilter.CUSTOM)) {
            try {
                start = LocalDate.parse(startDate);
                end = LocalDate.parse(endDate);
                isMonthlyGroup = DAYS.between(start, end) > 60;
            } catch (Exception e) {
                log.error("Invalid date format for custom date range: {} to {}", startDate, endDate, e);
                throw new IllegalArgumentException("Invalid date format for custom date range");
            }
        }

        // 1) fetch all metrics in window
        List<CampaignMetric> currentMetrics = campaignMetricRepository
                .findByCampaign_MarketingChannel_IdAndStatsDateBetween(
                        marketingChannelId, start, end);

        // 2) group either by day or by YearMonth
        // — daily grouping —
        Map<LocalDate, Integer> imprByDay = new TreeMap<>();
        Map<LocalDate, Integer> clicksByDay = new TreeMap<>();
        Map<LocalDate, Integer> convByDay = new TreeMap<>();
        Map<LocalDate, BigDecimal> costByDay = new TreeMap<>();
        for (var cm : currentMetrics) {
            LocalDate d = cm.getStatsDate();
            imprByDay.merge(d, cm.getImpressions(), Integer::sum);
            clicksByDay.merge(d, cm.getClicks(), Integer::sum);
            convByDay.merge(d, cm.getConversions(), Integer::sum);
            costByDay.merge(d, cm.getCost(), BigDecimal::add);
        }

        // — monthly grouping (THIS_YEAR) —
        Map<YearMonth, Integer> imprByMonth = null;
        Map<YearMonth, Integer> clicksByMonth = null;
        Map<YearMonth, Integer> convByMonth = null;
        Map<YearMonth, BigDecimal> costByMonth = null;
        if (isMonthlyGroup) {
            imprByMonth = currentMetrics.stream().collect(Collectors.groupingBy(
                    cm -> YearMonth.from(cm.getStatsDate()),
                    TreeMap::new,
                    Collectors.summingInt(CampaignMetric::getImpressions)
            ));
            clicksByMonth = currentMetrics.stream().collect(Collectors.groupingBy(
                    cm -> YearMonth.from(cm.getStatsDate()),
                    TreeMap::new,
                    Collectors.summingInt(CampaignMetric::getClicks)
            ));
            convByMonth = currentMetrics.stream().collect(Collectors.groupingBy(
                    cm -> YearMonth.from(cm.getStatsDate()),
                    TreeMap::new,
                    Collectors.summingInt(CampaignMetric::getConversions)
            ));
            costByMonth = currentMetrics.stream().collect(Collectors.groupingBy(
                    cm -> YearMonth.from(cm.getStatsDate()),
                    TreeMap::new,
                    Collectors.reducing(BigDecimal.ZERO, CampaignMetric::getCost, BigDecimal::add)
            ));
        }

        // 3) compute current‐period totals
        int totalImpr = (isMonthlyGroup
                ? imprByMonth.values().stream().mapToInt(i -> i).sum()
                : imprByDay.values().stream().mapToInt(i -> i).sum());
        int totalClicks = (isMonthlyGroup
                ? clicksByMonth.values().stream().mapToInt(i -> i).sum()
                : clicksByDay.values().stream().mapToInt(i -> i).sum());
        int totalConv = (isMonthlyGroup
                ? convByMonth.values().stream().mapToInt(i -> i).sum()
                : convByDay.values().stream().mapToInt(i -> i).sum());
        BigDecimal totalCost = (isMonthlyGroup
                ? costByMonth.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                : costByDay.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));

        // 4) compute previous‐period window & totals
        LocalDate prevStart, prevEnd;
        if (isMonthlyGroup) {
            prevStart = start.minusYears(1).withDayOfYear(1);
            prevEnd = prevStart.withDayOfYear(prevStart.lengthOfYear());
        } else {
            long days = DAYS.between(start, end) + 1;
            prevEnd = start.minusDays(1);
            prevStart = prevEnd.minusDays(days - 1);
        }
        List<CampaignMetric> prevMetrics = campaignMetricRepository
                .findByCampaign_MarketingChannel_IdAndStatsDateBetween(
                        marketingChannelId, prevStart, prevEnd);

        int prevImpr = prevMetrics.stream().mapToInt(CampaignMetric::getImpressions).sum();
        int prevClicks = prevMetrics.stream().mapToInt(CampaignMetric::getClicks).sum();
        int prevConv = prevMetrics.stream().mapToInt(CampaignMetric::getConversions).sum();
        BigDecimal prevCost = prevMetrics.stream()
                .map(CampaignMetric::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5) build the four core metrics
        MetricStats<Integer> imprStats = buildIntStats(
                isMonthlyGroup, imprByDay, imprByMonth, totalImpr, prevImpr);
        MetricStats<Integer> clicksStats = buildIntStats(
                isMonthlyGroup, clicksByDay, clicksByMonth, totalClicks, prevClicks);
        MetricStats<Integer> convStats = buildIntStats(
                isMonthlyGroup, convByDay, convByMonth, totalConv, prevConv);
        MetricStats<BigDecimal> costStats = buildDecStats(
                isMonthlyGroup, costByDay, costByMonth, totalCost, prevCost);

        // 6) cost‐per‐conversion & conversion‐rate
        MetricStats<BigDecimal> cpcStats = buildCostPerConversionStats(
                isMonthlyGroup,
                costByDay, costByMonth,
                convByDay, convByMonth,
                totalCost, totalConv,
                prevCost, prevConv
        );
        MetricStats<BigDecimal> crStats = buildConversionRateStats(
                isMonthlyGroup,
                convByDay, convByMonth,
                clicksByDay, clicksByMonth,
                totalConv, totalClicks,
                prevConv, prevClicks
        );

        // 7) return the full dashboard DTO
        return new WidgetAdsStats(
                imprStats,
                clicksStats,
                convStats,
                costStats,
                cpcStats,
                crStats
        );
    }

    public CampaignAdsStatsGraph getCampaignAdsStatsGraph(
            Integer marketingChannelId,
            DateFilter dateRange,
            String startDate,
            String endDate,
            MetricFilter metricFilter
    ) {
        LocalDate start = dateRange.getStartDate();
        LocalDate end = dateRange.getEndDate();
        boolean isMonthlyGroup = "MONTH".equals(dateRange.getType());

        if (dateRange.equals(DateFilter.CUSTOM)) {
            try {
                start = LocalDate.parse(startDate);
                end = LocalDate.parse(endDate);
                isMonthlyGroup = DAYS.between(start, end) > 60;
            } catch (Exception e) {
                log.error("Invalid date format for custom date range: {} to {}", startDate, endDate, e);
                throw new IllegalArgumentException("Invalid date format for custom date range");
            }
        }

        // 1) fetch all metrics in window
        List<CampaignMetric> metrics = campaignMetricRepository
                .findByCampaign_MarketingChannel_IdAndStatsDateBetween(
                        marketingChannelId,
                        start,
                        end
                );

        // 2) build the list of labels (strings)
        List<String> labels = isMonthlyGroup
                ? metrics.stream()
                .map(cm -> YearMonth.from(cm.getStatsDate()))
                .distinct()
                .sorted()
                .map(YearMonth::toString)
                .toList()
                : metrics.stream()
                .map(cm -> cm.getStatsDate().toString())
                .distinct()
                .sorted()
                .toList();

        // 3) group metrics by campaign name and by period key
        boolean finalIsMonthlyGroup = isMonthlyGroup;
        Map<String, Map<String, List<CampaignMetric>>> grouped = metrics.stream()
                .collect(Collectors.groupingBy(
                        cm -> cm.getCampaign().getName(),
                        Collectors.groupingBy(cm ->
                                finalIsMonthlyGroup
                                        ? YearMonth.from(cm.getStatsDate()).toString()
                                        : cm.getStatsDate().toString()
                        )
                ));

        // 4) for each campaign, build a list of metric values aligned to labels
        List<Map<String, List<Integer>>> campaignValues = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            String campaignName = entry.getKey();
            Map<String, List<CampaignMetric>> byPeriod = entry.getValue();

            List<Integer> values = labels.stream()
                    .map(label -> byPeriod.getOrDefault(label, List.of()).stream()
                            .mapToInt(cm -> switch (metricFilter) {
                                case CLICKS -> cm.getClicks();
                                case IMPRESSIONS -> cm.getImpressions();
                                case CONVERSIONS -> cm.getConversions();
                            })
                            .sum()
                    )
                    .toList();

            campaignValues.add(Map.of(campaignName, values));
        }

        // 5) compute total cost per campaign
        Map<String, BigDecimal> campaignCostsRelatedValues = grouped.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,                        // campaign name
                        e -> {
                            // sum up all costs for this campaign
                            BigDecimal totalCost = e.getValue().values().stream()
                                    .flatMap(List::stream)
                                    .map(CampaignMetric::getCost)      // assume BigDecimal getCost()
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                            // sum up all metrics for this campaign
                            int totalMetric = e.getValue().values().stream()
                                    .flatMap(List::stream)
                                    .mapToInt(cm -> switch (metricFilter) {
                                        case CLICKS -> cm.getClicks();
                                        case IMPRESSIONS -> cm.getImpressions();
                                        case CONVERSIONS -> cm.getConversions();
                                    })
                                    .sum();

                            // avoid division-by-zero
                            if (totalMetric == 0) {
                                return BigDecimal.ZERO;
                            }

                            // cost per metric
                            return totalCost
                                    .divide(BigDecimal.valueOf(totalMetric), 2, RoundingMode.HALF_UP);
                        }
                ));

        // 6) return with all three fields
        return new CampaignAdsStatsGraph(
                campaignValues,
                campaignCostsRelatedValues,
                labels
        );
    }

    public List<CampaignAdsStatsTableRow> getCampaignAdsStatsTable(
            Integer marketingChannelId,
            DateFilter dateRange,
            String startDate,
            String endDate
    ) {
        LocalDate start = dateRange.getStartDate();
        LocalDate end = dateRange.getEndDate();

        if (dateRange.equals(DateFilter.CUSTOM)) {
            try {
                start = LocalDate.parse(startDate);
                end = LocalDate.parse(endDate);
            } catch (Exception e) {
                log.error("Invalid date format for custom date range: {} to {}", startDate, endDate, e);
                throw new IllegalArgumentException("Invalid date format for custom date range");
            }
        }

        // — determine previous period window —
        long days = DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(days - 1);

        // — fetch metrics for both periods, only ACTIVE/ENABLED campaigns —
        List<CampaignMetric> currMetrics = campaignMetricRepository
                .findByCampaign_MarketingChannel_IdAndStatsDateBetween(
                        marketingChannelId, start, end);
        List<CampaignMetric> prevMetrics = campaignMetricRepository
                .findByCampaign_MarketingChannel_IdAndStatsDateBetween(
                        marketingChannelId, prevStart, prevEnd);

        // — group by Campaign —
        Map<Campaign, List<CampaignMetric>> currByCamp = currMetrics.stream()
                .collect(Collectors.groupingBy(CampaignMetric::getCampaign));
        Map<Campaign, List<CampaignMetric>> prevByCamp = prevMetrics.stream()
                .collect(Collectors.groupingBy(CampaignMetric::getCampaign));

        List<CampaignAdsStatsTableRow> rows = new ArrayList<>();
        for (var entry : currByCamp.entrySet()) {
            Campaign campaign = entry.getKey();
            List<CampaignMetric> cmCurr = entry.getValue();
            List<CampaignMetric> cmPrev = prevByCamp.getOrDefault(campaign, List.of());

            // — current totals —
            int currImpr = cmCurr.stream().mapToInt(CampaignMetric::getImpressions).sum();
            int currClicks = cmCurr.stream().mapToInt(CampaignMetric::getClicks).sum();
            int currConv = cmCurr.stream().mapToInt(CampaignMetric::getConversions).sum();
            BigDecimal currCost = cmCurr.stream()
                    .map(CampaignMetric::getCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal currCPC = currConv > 0
                    ? currCost.divide(BigDecimal.valueOf(currConv), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal currCR = currClicks > 0
                    ? BigDecimal.valueOf(currConv)
                    .divide(BigDecimal.valueOf(currClicks), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            // — previous totals —
            int prevImpr = cmPrev.stream().mapToInt(CampaignMetric::getImpressions).sum();
            int prevClicks = cmPrev.stream().mapToInt(CampaignMetric::getClicks).sum();
            int prevConv = cmPrev.stream().mapToInt(CampaignMetric::getConversions).sum();
            BigDecimal prevCost = cmPrev.stream()
                    .map(CampaignMetric::getCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal prevCPC = prevConv > 0
                    ? prevCost.divide(BigDecimal.valueOf(prevConv), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal prevCR = prevClicks > 0
                    ? BigDecimal.valueOf(prevConv)
                    .divide(BigDecimal.valueOf(prevClicks), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            // — percent changes —
            BigDecimal imprPct = percentChange(BigDecimal.valueOf(currImpr), BigDecimal.valueOf(prevImpr));
            BigDecimal clicksPct = percentChange(BigDecimal.valueOf(currClicks), BigDecimal.valueOf(prevClicks));
            BigDecimal convPct = percentChange(BigDecimal.valueOf(currConv), BigDecimal.valueOf(prevConv));
            BigDecimal costPct = percentChange(currCost, prevCost);
            BigDecimal cpcPct = percentChange(currCPC, prevCPC);
            BigDecimal crPct = percentChange(currCR, prevCR);

            // — wrap into MetricStats (empty labels/values) —
            var imprStats = new MetricStats<>(List.of(), List.of(), currImpr, imprPct);
            var clicksStats = new MetricStats<>(List.of(), List.of(), currClicks, clicksPct);
            var convStats = new MetricStats<>(List.of(), List.of(), currConv, convPct);
            var costStats = new MetricStats<>(List.of(), List.of(), currCost, costPct);
            var cpcStats = new MetricStats<>(List.of(), List.of(), currCPC, cpcPct);
            var crStats = new MetricStats<>(List.of(), List.of(), currCR, crPct);

            // — assemble row —
            rows.add(new CampaignAdsStatsTableRow(
                    campaign.getName(),
                    campaign.getStatus(),    // assuming getStatus() returns CampaignStatus
                    imprStats,
                    clicksStats,
                    convStats,
                    costStats,
                    cpcStats,
                    crStats
            ));
        }

        return rows;
    }

    // ——— helpers ———

    private BigDecimal percentChange(BigDecimal curr, BigDecimal prev) {
        if (prev.compareTo(BigDecimal.ZERO) == 0) {
            return curr.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(100);
        }
        return curr
                .subtract(prev)
                .divide(prev, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private MetricStats<Integer> buildIntStats(
            boolean isYear,
            Map<LocalDate, Integer> daily,
            Map<YearMonth, Integer> monthly,
            int total,
            int prevTotal
    ) {
        // choose the right series & labels
        List<String> labels = (isYear
                ? monthly.keySet().stream().map(YearMonth::toString)
                : daily.keySet().stream().map(LocalDate::toString)
        ).toList();

        Collection<Integer> values = (isYear
                ? monthly.values()
                : daily.values()
        );

        BigDecimal pct = percentChange(
                BigDecimal.valueOf(total),
                BigDecimal.valueOf(prevTotal)
        );

        return new MetricStats<>(labels, values, total, pct);
    }

    private MetricStats<BigDecimal> buildDecStats(
            boolean isYear,
            Map<LocalDate, BigDecimal> daily,
            Map<YearMonth, BigDecimal> monthly,
            BigDecimal total,
            BigDecimal prevTotal
    ) {
        List<String> labels = (isYear
                ? monthly.keySet().stream().map(YearMonth::toString)
                : daily.keySet().stream().map(LocalDate::toString)
        ).toList();

        Collection<BigDecimal> values = (isYear
                ? monthly.values()
                : daily.values()
        );

        BigDecimal pct = percentChange(total, prevTotal);
        return new MetricStats<>(labels, values, total, pct);
    }

    private MetricStats<BigDecimal> buildCostPerConversionStats(
            boolean isYear,
            Map<LocalDate, BigDecimal> dailyCost,
            Map<YearMonth, BigDecimal> monthlyCost,
            Map<LocalDate, Integer> dailyConv,
            Map<YearMonth, Integer> monthlyConv,
            BigDecimal totalCost,
            int totalConv,
            BigDecimal prevCost,
            int prevConv
    ) {
        // pick source maps
        Map<?, BigDecimal> costMap = isYear ? monthlyCost : dailyCost;
        Map<?, Integer> convMap = isYear ? monthlyConv : dailyConv;

        // compute per‐bucket series
        Map<Object, BigDecimal> series = new TreeMap<>();
        costMap.forEach((k, c) -> {
            int conv = convMap.getOrDefault(k, 0);
            if (conv > 0) {
                series.put(k, c.divide(BigDecimal.valueOf(conv), 4, RoundingMode.HALF_UP));
            } else {
                series.put(k, BigDecimal.ZERO); // no conversions, set to zero
            }
        });

        List<String> labels = series.keySet().stream()
                .map(Object::toString).toList();
        Collection<BigDecimal> values = series.values();

        BigDecimal avgThis = (totalConv > 0)
                ? totalCost.divide(BigDecimal.valueOf(totalConv), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgPrev = (prevConv > 0)
                ? prevCost.divide(BigDecimal.valueOf(prevConv), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal pct = percentChange(avgThis, avgPrev);

        return new MetricStats<>(labels, values, avgThis, pct);
    }

    private MetricStats<BigDecimal> buildConversionRateStats(
            boolean isYear,
            Map<LocalDate, Integer> dailyConv,
            Map<YearMonth, Integer> monthlyConv,
            Map<LocalDate, Integer> dailyClicks,
            Map<YearMonth, Integer> monthlyClicks,
            int totalConv,
            int totalClicks,
            int prevConv,
            int prevClicks
    ) {
        Map<?, Integer> convMap = isYear ? monthlyConv : dailyConv;
        Map<?, Integer> clickMap = isYear ? monthlyClicks : dailyClicks;

        Map<Object, BigDecimal> series = new TreeMap<>();
        clickMap.forEach((k, clicks) -> {
            int conv = convMap.getOrDefault(k, 0);
            if (clicks > 0) {
                BigDecimal rate = BigDecimal.valueOf(conv)
                        .divide(BigDecimal.valueOf(clicks), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                series.put(k, rate);
            }
        });

        List<String> labels = series.keySet().stream()
                .map(Object::toString).toList();
        Collection<BigDecimal> values = series.values();

        BigDecimal rateThis = (totalClicks > 0)
                ? BigDecimal.valueOf(totalConv)
                .divide(BigDecimal.valueOf(totalClicks), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        BigDecimal ratePrev = (prevClicks > 0)
                ? BigDecimal.valueOf(prevConv)
                .divide(BigDecimal.valueOf(prevClicks), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        BigDecimal pct = percentChange(rateThis, ratePrev);

        return new MetricStats<>(labels, values, rateThis, pct);
    }


}

package com.premiergroup.ad_metrics_hub.service;

import com.premiergroup.ad_metrics_hub.dto.WidgetAdsStats;
import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import com.premiergroup.ad_metrics_hub.enums.DateFilter;
import com.premiergroup.ad_metrics_hub.repository.CampaignMetricRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Log4j2
@AllArgsConstructor
public class AdStatsService {

    private final CampaignMetricRepository campaignMetricRepository;

    public WidgetAdsStats getStatsByMarketingChannelIdAndDateRange(Integer marketingChannelId, DateFilter dateRange) {

        List<CampaignMetric> campaignMetrics = campaignMetricRepository.findByCampaign_MarketingChannel_IdAndStatsDateBetween(
                marketingChannelId,
                dateRange.getStartDate(),
                dateRange.getEndDate());

        // Get impressions, clicks, conversions, and cost as lists of strings
        Map<LocalDate, Integer> sumImpressionsByDate = new TreeMap<>();
        Map<LocalDate, Integer> sumClicksByDate = new TreeMap<>();
        Map<LocalDate, Integer> sumConversionsByDate = new TreeMap<>();
        Map<LocalDate, BigDecimal> sumCostByDate = new TreeMap<>();

        for (CampaignMetric campaignMetric : campaignMetrics) {
            log.debug("Processing campaign metric: {}", campaignMetric);

            sumImpressionsByDate.merge(campaignMetric.getStatsDate(), campaignMetric.getImpressions(), Integer::sum);
            sumClicksByDate.merge(campaignMetric.getStatsDate(), campaignMetric.getClicks(), Integer::sum);
            sumConversionsByDate.merge(campaignMetric.getStatsDate(), campaignMetric.getConversions(), Integer::sum);
            sumCostByDate.merge(campaignMetric.getStatsDate(), campaignMetric.getCost(), BigDecimal::add);
        }

        return new WidgetAdsStats(sumImpressionsByDate.values(),
                sumClicksByDate.values(),
                sumConversionsByDate.values(),
                sumCostByDate.values());
    }
}

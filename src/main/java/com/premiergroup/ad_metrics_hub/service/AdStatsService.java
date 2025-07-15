package com.premiergroup.ad_metrics_hub.service;

import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import com.premiergroup.ad_metrics_hub.enums.DateFilter;
import com.premiergroup.ad_metrics_hub.repository.CampaignMetricRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Log4j2
@AllArgsConstructor
public class AdStatsService {

    private final CampaignMetricRepository campaignMetricRepository;

    public List<CampaignMetric> getStatsByMarketingChannelIdAndDateRange(Integer marketingChannelId, DateFilter dateRange) {

        List<CampaignMetric> campaignMetrics = campaignMetricRepository.findByCampaign_MarketingChannel_IdAndStatsDateBetween(
                marketingChannelId,
                dateRange.getStartDate(),
                dateRange.getEndDate());

        for (CampaignMetric metric : campaignMetrics) {
            log.info("Fetched CampaignMetric: {}", metric);
        }

        return campaignMetrics;
    }
}

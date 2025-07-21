package com.premiergroup.ad_metrics_hub.repository;

import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CampaignMetricRepository extends JpaRepository<CampaignMetric, Integer> {

    List<CampaignMetric> findByCampaign_MarketingChannel_IdAndStatsDateBetween(
            Integer marketingChannelsId,
            LocalDate start,
            LocalDate end
    );

    List<CampaignMetric> findByCampaign_MarketingChannel_IdAndCampaign_StatusInAndStatsDateBetween(
            Integer marketingChannelId,
            List<String> statuses,
            LocalDate start,
            LocalDate end
    );
}
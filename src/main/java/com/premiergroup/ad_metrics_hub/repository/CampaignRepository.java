package com.premiergroup.ad_metrics_hub.repository;

import com.premiergroup.ad_metrics_hub.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Integer> {

    Optional<Campaign> findByMarketingChannel_IdAndCampaignId(
            Integer marketingChannelsId,
            String campaignId
    );


}
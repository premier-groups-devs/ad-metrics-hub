package com.premiergroup.ad_metrics_hub.repository;

import com.premiergroup.ad_metrics_hub.entity.MarketingChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketingChannelRepository extends JpaRepository<MarketingChannel, Integer> {

    Optional<MarketingChannel> findBySourceName(String sourceName);
}

package com.premiergroup.ad_metrics_hub.controller;

import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import com.premiergroup.ad_metrics_hub.enums.DateFilter;
import com.premiergroup.ad_metrics_hub.service.AdStatsService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stats")
@AllArgsConstructor
public class AdStatsController {

    private final AdStatsService service;

    @GetMapping
    public ResponseEntity<List<CampaignMetric>> getStatsByMarketingChannelIdAndDateRange(
            @RequestParam Integer marketingChannelId,
            @RequestParam DateFilter dateRange
    ) {
        //TODO MAKE SCHEDULED TASK TO FETCH STATS FROM THE API AND SAVE THEM IN THE DB
        //TODO MAKE DTO ACCORDING TO THE GRAPH IN THE FRONTEND
        List<CampaignMetric> campaignMetrics = service.getStatsByMarketingChannelIdAndDateRange(marketingChannelId, dateRange);

        if (campaignMetrics.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(campaignMetrics);
    }
}

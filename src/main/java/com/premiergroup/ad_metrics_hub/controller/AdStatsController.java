package com.premiergroup.ad_metrics_hub.controller;

import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import com.premiergroup.ad_metrics_hub.enums.DateFilter;
import com.premiergroup.ad_metrics_hub.service.AdStatsService;
import com.premiergroup.ad_metrics_hub.service.GoogleAdsAPIService;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ads")
@AllArgsConstructor
public class AdStatsController {

    private final AdStatsService adStatsService;
    private final GoogleAdsAPIService googleAdsAPIService;

    @GetMapping("/stats")
    public ResponseEntity<List<CampaignMetric>> getStatsByMarketingChannelIdAndDateRange(
            @RequestParam Integer marketingChannelId,
            @RequestParam DateFilter dateRange
    ) {
        //TODO MAKE SCHEDULED TASK TO FETCH STATS FROM THE API AND SAVE THEM IN THE DB
        //TODO MAKE DTO ACCORDING TO THE GRAPH IN THE FRONTEND
    //TODO MAKE METHOD TO SAVE DATA FROM BING ADS
        List<CampaignMetric> campaignMetrics = adStatsService.getStatsByMarketingChannelIdAndDateRange(marketingChannelId, dateRange);

        if (campaignMetrics.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(campaignMetrics);
    }

    /**
     * Trigger a manual sync of campaigns and metrics for the given customer ID.
     * <p>
     * Example: POST api/ads/sync?customerId=123456678&marketingChannelId=1
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> syncCampaigns(
            @RequestParam("customerId")
            @Positive(message = "customerId must be positive")
            long customerId,
            @RequestParam("marketingChannelId")
            @Positive(message = "marketingChannelId must be positive")
            int marketingChannelId) {

        googleAdsAPIService.syncCampaignsAndMetrics(customerId, marketingChannelId);
        return ResponseEntity.ok().build();
    }
}

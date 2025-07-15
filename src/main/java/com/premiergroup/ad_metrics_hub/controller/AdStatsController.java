package com.premiergroup.ad_metrics_hub.controller;

import com.microsoft.bingads.v13.campaignmanagement.AdApiFaultDetail_Exception;
import com.microsoft.bingads.v13.campaignmanagement.ApiFaultDetail_Exception;
import com.premiergroup.ad_metrics_hub.config.GoogleAdsConfig;
import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import com.premiergroup.ad_metrics_hub.enums.DateFilter;
import com.premiergroup.ad_metrics_hub.service.AdStatsService;
import com.premiergroup.ad_metrics_hub.service.BingAdsAPIService;
import com.premiergroup.ad_metrics_hub.service.GoogleAdsAPIService;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/ads")
@AllArgsConstructor
public class AdStatsController {

    private final AdStatsService adStatsService;
    private final GoogleAdsAPIService googleAdsAPIService;
    private final BingAdsAPIService bingAdsAPIService;

    @GetMapping("/stats")
    public ResponseEntity<Integer> getStatsByMarketingChannelIdAndDateRange(
            @RequestParam Integer marketingChannelId,
            @RequestParam DateFilter dateRange
    ) {
        //TODO MAKE SCHEDULED TASK TO FETCH STATS FROM THE API AND SAVE THEM IN THE DB
        //TODO MAKE DTO ACCORDING TO THE GRAPH IN THE FRONTEND
        List<CampaignMetric> campaignMetrics = adStatsService.getStatsByMarketingChannelIdAndDateRange(marketingChannelId, dateRange);

        if (campaignMetrics.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(campaignMetrics.size());
    }

    /**
     * Trigger a manual sync of campaigns and metrics for the given customer ID.
     * <p>
     * Example: POST api/ads/sync?customerId=123456678&marketingChannelId=1
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> syncCampaigns(
            @RequestParam("marketingChannelId")
            @Positive(message = "marketingChannelId must be positive")
            int marketingChannelId) throws ApiFaultDetail_Exception, AdApiFaultDetail_Exception, ExecutionException, InterruptedException {

        if (1 == marketingChannelId) { //Google Ads

            googleAdsAPIService.syncCampaignsAndMetrics(GoogleAdsConfig.CUSTOMER_ID, marketingChannelId);
        } else if (5 == marketingChannelId) { //Bing Ads

            bingAdsAPIService.syncCampaigns(marketingChannelId);
            bingAdsAPIService.syncMetrics(marketingChannelId);
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().build();
    }
}

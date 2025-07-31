package com.premiergroup.ad_metrics_hub.controller;

import com.microsoft.bingads.v13.campaignmanagement.AdApiFaultDetail_Exception;
import com.microsoft.bingads.v13.campaignmanagement.ApiFaultDetail_Exception;
import com.premiergroup.ad_metrics_hub.dto.CampaignAdsStatsGraph;
import com.premiergroup.ad_metrics_hub.dto.CampaignAdsStatsTableRow;
import com.premiergroup.ad_metrics_hub.dto.WidgetAdsStats;
import com.premiergroup.ad_metrics_hub.enums.DateFilter;
import com.premiergroup.ad_metrics_hub.enums.MetricFilter;
import com.premiergroup.ad_metrics_hub.service.AdStatsService;
import com.premiergroup.ad_metrics_hub.service.BingAdsAPIService;
import com.premiergroup.ad_metrics_hub.service.GoogleAdsAPIService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
public class AdStatsController {

    private final AdStatsService adStatsService;
    private final GoogleAdsAPIService googleAdsAPIService;
    private final BingAdsAPIService bingAdsAPIService;

    @Value("${google.ads.customer-id}")
    private long customerId;

    @GetMapping("/widget-ads-stats")
    public ResponseEntity<WidgetAdsStats> getSWidgetAdsStats(
            @RequestParam Integer marketingChannelId,
            @RequestParam DateFilter dateRange
    ) {
        //TODO GET STATS FROM DEVICES
        WidgetAdsStats widgetStats = adStatsService.getSWidgetAdsStats(marketingChannelId, dateRange);

        if (widgetStats == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(widgetStats);
    }

    @GetMapping("/campaign-ads-stats-graph")
    public ResponseEntity<CampaignAdsStatsGraph> getCampaignAdsStatsGraph(
            @RequestParam Integer marketingChannelId,
            @RequestParam DateFilter dateRange,
            @RequestParam MetricFilter metric
    ) {
        CampaignAdsStatsGraph campaignAdsStatsGraph = adStatsService.getCampaignAdsStatsGraph(marketingChannelId, dateRange, metric);
        if (campaignAdsStatsGraph == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(campaignAdsStatsGraph);
    }

    @GetMapping("/campaign-ads-stats-table")
    public ResponseEntity<List<CampaignAdsStatsTableRow>> getCampaignAdsStatsTable(
            @RequestParam Integer marketingChannelId,
            @RequestParam DateFilter dateRange
    ) {
        List<CampaignAdsStatsTableRow> campaignAdsStatsTableRowList = adStatsService.getCampaignAdsStatsTable(marketingChannelId, dateRange);
        if (campaignAdsStatsTableRowList.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(campaignAdsStatsTableRowList);
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
            googleAdsAPIService.syncCampaignsAndMetrics(customerId, marketingChannelId);
        } else if (5 == marketingChannelId) { //Bing Ads

            bingAdsAPIService.syncCampaigns(marketingChannelId);
            bingAdsAPIService.syncAllMetrics(marketingChannelId);
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().build();
    }
}

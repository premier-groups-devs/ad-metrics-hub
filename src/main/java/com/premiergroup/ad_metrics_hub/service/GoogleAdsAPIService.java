package com.premiergroup.ad_metrics_hub.service;

import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v20.errors.GoogleAdsException;
import com.google.ads.googleads.v20.services.GoogleAdsRow;
import com.google.ads.googleads.v20.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v20.services.SearchGoogleAdsStreamRequest;
import com.google.ads.googleads.v20.services.SearchGoogleAdsStreamResponse;
import com.premiergroup.ad_metrics_hub.entity.Campaign;
import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import com.premiergroup.ad_metrics_hub.entity.MarketingChannel;
import com.premiergroup.ad_metrics_hub.repository.CampaignMetricRepository;
import com.premiergroup.ad_metrics_hub.repository.CampaignRepository;
import com.premiergroup.ad_metrics_hub.repository.MarketingChannelRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Log4j2
@RequiredArgsConstructor
public class GoogleAdsAPIService {

    private final GoogleAdsClient googleAdsClient;
    private final CampaignRepository campaignRepository;
    private final CampaignMetricRepository metricRepository;
    private final MarketingChannelRepository channelRepository;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${google.ads.customer-id}")
    private long customerId;

    /**
     * Scheduled task to sync Google Ads campaigns and metrics daily each hour at 58 minutes past the hour.
     * Runs every hour at the top of the hour.
     */
    @Scheduled(cron = "0 58 * * * *")
    @Transactional
    public void dailyGoogleAdsSync() {
        Integer marketingChannelId = 1;                     //Google Ads channel ID

        log.info("Starting scheduled Google Ads sync");
        // load the channel
        MarketingChannel channel = channelRepository.findById(marketingChannelId)
                .orElseThrow(() ->
                        new IllegalStateException("MarketingChannel not found: " + marketingChannelId)
                );

        // ensure campaigns are up to date
        List<Campaign> campaigns = listAndSaveCampaigns(customerId, channel);

        // Sync metrics for the last day
        campaigns.forEach(c ->
                saveMetrics(customerId, c, LocalDate.now().minusDays(1), LocalDate.now())
        );
        log.info("Completed scheduled Google Ads sync");
    }

    @Transactional
    public void syncCampaignsAndMetrics(long customerId, Integer marketingChannelId) {
        MarketingChannel channel = channelRepository.findById(marketingChannelId)
                .orElseThrow(() -> new IllegalStateException(
                        "MarketingChannel not found: " + marketingChannelId));

        List<Campaign> saved = listAndSaveCampaigns(customerId, channel);
        LocalDate start = getFirstStatDate(googleAdsClient, customerId);
//        LocalDate end = LocalDate.now().minusDays(1); // yesterday
        LocalDate end = LocalDate.now();

        //Additionally, call dailyGoogleAdsStats scheduled task for daily updates
        saved.forEach(campaign -> saveMetrics(customerId, campaign, start, end));
    }

    /**
     * Lists campaigns and persists/updates each.
     */
    private List<Campaign> listAndSaveCampaigns(long customerId, MarketingChannel marketingChannel) {
        String query =
                "SELECT campaign.id, campaign.name, campaign.status " +
                        "FROM campaign ORDER BY campaign.id";

        List<Campaign> persisted = new ArrayList<>();
        try (GoogleAdsServiceClient service = googleAdsClient.getLatestVersion()
                .createGoogleAdsServiceClient()) {

            SearchGoogleAdsStreamRequest req = SearchGoogleAdsStreamRequest.newBuilder()
                    .setCustomerId(Long.toString(customerId))
                    .setQuery(query)
                    .build();

            for (SearchGoogleAdsStreamResponse resp : service.searchStreamCallable().call(req)) {
                for (GoogleAdsRow row : resp.getResultsList()) {
                    String campaignId = String.valueOf(row.getCampaign().getId());
                    Optional<Campaign> opt = campaignRepository
                            .findByMarketingChannel_IdAndCampaignId(marketingChannel.getId(), campaignId);
                    Campaign camp = opt.orElseGet(() -> Campaign.builder()
                            .marketingChannel(marketingChannel)
                            .campaignId(campaignId)
                            .name(row.getCampaign().getName())
                            .status(row.getCampaign().getStatus().name())
                            .build());

                    persisted.add(campaignRepository.save(camp));
                }
            }
        } catch (GoogleAdsException e) {
            log.error("Error listing campaigns: {}", e.getMessage());
        }
        return persisted;
    }

    /**
     * Fetches and saves metrics for a specific campaign
     */
    private void saveMetrics(long customerId, Campaign campaign,
                             LocalDate startDate, LocalDate endDate) {

        String start = startDate.format(fmt);
        String end = endDate.format(fmt);

        String query = String.join(" ", List.of(
                "SELECT segments.date, metrics.clicks, metrics.impressions, metrics.cost_micros,",
                "metrics.ctr, metrics.average_cpc, metrics.conversions,",
                "metrics.cost_per_conversion, metrics.all_conversions,",
                "metrics.all_conversions_value, metrics.value_per_conversion",
                "FROM campaign",
                "WHERE campaign.id =", String.valueOf(campaign.getCampaignId()),
                "AND segments.date BETWEEN '" + start + "' AND '" + end + "'",
                "ORDER BY segments.date"
        ));

        try (GoogleAdsServiceClient service = googleAdsClient.getLatestVersion()
                .createGoogleAdsServiceClient()) {

            SearchGoogleAdsStreamRequest req = SearchGoogleAdsStreamRequest.newBuilder()
                    .setCustomerId(Long.toString(customerId))
                    .setQuery(query)
                    .build();

            for (SearchGoogleAdsStreamResponse resp : service.searchStreamCallable().call(req)) {
                for (GoogleAdsRow row : resp.getResultsList()) {
                    LocalDate date = LocalDate.parse(row.getSegments().getDate());
                    BigDecimal cost = BigDecimal.valueOf(
                            row.getMetrics().getCostMicros() / 1_000_000.0
                    );

                    // 1. Check if a metric for this date already exists
                    Optional<CampaignMetric> existingOpt =
                            metricRepository.findByCampaign_IdAndStatsDate(campaign.getId(), date);

                    // 2. If it exists, use it; otherwise create a new one
                    CampaignMetric metric = existingOpt.orElseGet(() -> CampaignMetric.builder()
                            .campaign(campaign)
                            .statsDate(date)
                            .build());

                    // 3. Populate fields (both for new and existing)
                    metric.setClicks(Math.toIntExact(row.getMetrics().getClicks()));
                    metric.setImpressions(Math.toIntExact(row.getMetrics().getImpressions()));
                    metric.setCost(cost);
                    metric.setCtr(BigDecimal.valueOf(row.getMetrics().getCtr()));
                    metric.setAvgCpc(BigDecimal.valueOf(
                            row.getMetrics().getAverageCpc() / 1_000_000.0
                    ));
                    metric.setConversions((int) row.getMetrics().getConversions());
                    metric.setConversionRate(BigDecimal.valueOf(
                            row.getMetrics().getClicks() > 0
                                    ? (row.getMetrics().getConversions() * 100.0 / row.getMetrics().getClicks())
                                    : 0.0
                    ));
                    metric.setCostPerConversion(BigDecimal.valueOf(
                            row.getMetrics().getCostPerConversion() / 1_000_000.0
                    ));
                    metric.setConversionValue(BigDecimal.valueOf(
                            row.getMetrics().getAllConversionsValue()
                    ));
                    metric.setValuePerConversion(BigDecimal.valueOf(
                            row.getMetrics().getValuePerConversion()
                    ));
                    metric.setRoas(cost.compareTo(BigDecimal.ZERO) > 0
                                    ? BigDecimal.valueOf(
                                    row.getMetrics().getAllConversionsValue() /
                                            (row.getMetrics().getCostMicros() / 1_000_000.0)
                            )
                                    : BigDecimal.ZERO
                    );

                    // 4. Save this metric (update or insert)
                    metricRepository.save(metric);
                }
            }
        } catch (GoogleAdsException e) {
            log.error("Error fetching metrics: {}", e.getMessage());
        }
    }

    /**
     * Runs a streaming GAQL query sorted by date ascending and returns the first date we
     * see. If no data is found, defaults to today.
     */
    private LocalDate getFirstStatDate(GoogleAdsClient client, long customerId) {
        LocalDate today = LocalDate.now();

        String startSentinel = "2000-01-01";              // far before any real data
        String endDate = today.format(fmt);

        String q = "SELECT segments.date "
                + "FROM campaign "
                + "WHERE campaign.status = 'ENABLED' "
                + "  AND segments.date BETWEEN '"
                + startSentinel + "' AND '"
                + endDate + "' "
                + "ORDER BY segments.date ASC "
                + "LIMIT 1";

        try (GoogleAdsServiceClient service =
                     client.getLatestVersion().createGoogleAdsServiceClient()) {

            SearchGoogleAdsStreamRequest req =
                    SearchGoogleAdsStreamRequest.newBuilder()
                            .setCustomerId(Long.toString(customerId))
                            .setQuery(q)
                            .build();

            for (SearchGoogleAdsStreamResponse resp :
                    service.searchStreamCallable().call(req)) {
                for (GoogleAdsRow row : resp.getResultsList()) {
                    return LocalDate.parse(row.getSegments().getDate());
                }
            }
        } catch (GoogleAdsException ex) {
            log.error("Warning: couldn’t fetch first stat date: {}", ex.getMessage());
        }
        return today;
    }
}

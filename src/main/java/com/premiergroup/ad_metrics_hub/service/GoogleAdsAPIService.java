package com.premiergroup.ad_metrics_hub.service;

import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v20.errors.GoogleAdsException;
import com.google.ads.googleads.v20.services.GoogleAdsRow;
import com.google.ads.googleads.v20.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v20.services.SearchGoogleAdsStreamRequest;
import com.google.ads.googleads.v20.services.SearchGoogleAdsStreamResponse;
import com.premiergroup.ad_metrics_hub.config.GoogleAdsConfig;
import com.premiergroup.ad_metrics_hub.entity.Campaign;
import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import com.premiergroup.ad_metrics_hub.entity.MarketingChannel;
import com.premiergroup.ad_metrics_hub.repository.CampaignMetricRepository;
import com.premiergroup.ad_metrics_hub.repository.CampaignRepository;
import com.premiergroup.ad_metrics_hub.repository.MarketingChannelRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Log4j2
@AllArgsConstructor
public class GoogleAdsAPIService {


    private final GoogleAdsClient googleAdsClient;
    private final CampaignRepository campaignRepository;
    private final CampaignMetricRepository metricRepository;
    private final MarketingChannelRepository channelRepository;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Every day at 1 AM, fetch yesterday's stats for all campaigns
     * under the given marketing channel and persist them.
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void dailyGoogleAdsStats() {
        long customerId = GoogleAdsConfig.CUSTOMER_ID;
        Integer marketingChannelId = 1;
        LocalDate yesterday = LocalDate.now().minusDays(1);

        log.info("Starting scheduled Google Ads sync for date {}", yesterday);
        // load the channel
        MarketingChannel channel = channelRepository.findById(marketingChannelId)
                .orElseThrow(() ->
                        new IllegalStateException("MarketingChannel not found: " + marketingChannelId)
                );

        // ensure campaigns are up to date
        List<Campaign> campaigns = listAndSaveCampaigns(customerId, channel);

        // fetch & save metrics for *only* yesterday
        campaigns.forEach(c ->
                saveMetrics(customerId, c, yesterday, yesterday)
        );
        log.info("Finished scheduled Google Ads sync for date {}", yesterday);
    }

    @Transactional
    public void syncCampaignsAndMetrics(long customerId, Integer marketingChannelId) {
        MarketingChannel channel = channelRepository.findById(marketingChannelId)
                .orElseThrow(() -> new IllegalStateException(
                        "MarketingChannel not found: " + marketingChannelId));

        List<Campaign> saved = listAndSaveCampaigns(customerId, channel);
        LocalDate start = getFirstStatDate(googleAdsClient, customerId);
        LocalDate end = LocalDate.now();

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
     * Fetches and saves daily metrics for one campaign over a date range.
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

        List<CampaignMetric> metrics = new ArrayList<>();
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

                    CampaignMetric cm = CampaignMetric.builder()
                            .campaign(campaign)
                            .statsDate(date)
                            .clicks(Math.toIntExact(row.getMetrics().getClicks()))
                            .impressions(Math.toIntExact(row.getMetrics().getImpressions()))
                            .cost(cost)
                            .ctr(BigDecimal.valueOf(row.getMetrics().getCtr()))
                            .avgCpc(BigDecimal.valueOf(
                                    row.getMetrics().getAverageCpc() / 1_000_000.0
                            ))
                            .conversions((int) row.getMetrics().getConversions())
                            .conversionRate(BigDecimal.valueOf(
                                    row.getMetrics().getClicks() > 0 ?
                                            (row.getMetrics().getConversions() * 100 / row.getMetrics().getClicks())
                                            : 0)
                            )
                            .costPerConversion(BigDecimal.valueOf(
                                    row.getMetrics().getCostPerConversion() / 1_000_000.0
                            ))
                            .conversionValue(BigDecimal.valueOf(
                                    row.getMetrics().getAllConversionsValue()
                            ))
                            .valuePerConversion(BigDecimal.valueOf(
                                    row.getMetrics().getValuePerConversion()
                            ))
                            .roas(cost.compareTo(BigDecimal.ZERO) > 0 ?
                                    BigDecimal.valueOf(
                                            row.getMetrics().getAllConversionsValue() /
                                                    (row.getMetrics().getCostMicros() / 1_000_000.0)
                                    ) : BigDecimal.ZERO
                            )
                            .build();
                    metrics.add(cm);
                }
            }
        } catch (GoogleAdsException e) {
            log.error("Error fetching metrics: {}", e.getMessage());
        }

        // cascade = ALL, so saving metrics will persist via campaign if set
        metricRepository.saveAll(metrics);
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

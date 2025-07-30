package com.premiergroup.ad_metrics_hub.service;

import com.microsoft.bingads.ApiEnvironment;
import com.microsoft.bingads.AuthorizationData;
import com.microsoft.bingads.ServiceClient;
import com.microsoft.bingads.v13.campaignmanagement.CampaignType;
import com.microsoft.bingads.v13.campaignmanagement.GetCampaignsByAccountIdRequest;
import com.microsoft.bingads.v13.campaignmanagement.GetCampaignsByAccountIdResponse;
import com.microsoft.bingads.v13.campaignmanagement.ICampaignManagementService;
import com.microsoft.bingads.v13.reporting.*;
import com.premiergroup.ad_metrics_hub.entity.Campaign;
import com.premiergroup.ad_metrics_hub.entity.CampaignMetric;
import com.premiergroup.ad_metrics_hub.entity.MarketingChannel;
import com.premiergroup.ad_metrics_hub.repository.CampaignMetricRepository;
import com.premiergroup.ad_metrics_hub.repository.CampaignRepository;
import com.premiergroup.ad_metrics_hub.repository.MarketingChannelRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@Service
@Log4j2
@AllArgsConstructor
public class BingAdsAPIService {

    private final AuthorizationData authorizationData;
    private final CampaignRepository campaignRepository;
    private final CampaignMetricRepository metricRepository;
    private final MarketingChannelRepository channelRepository;

    /**
     * Runs every day at 2:10 AM to sync Bing Ads campaigns and metrics.
     */
    @Scheduled(cron = "0 10 2 * * ?")
    public void dailyBingAdsSync() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int marketingChannelId = 5;                     //Bing Ads channel ID

        try {
            log.info("Starting daily Bing Ads sync for date {}", yesterday);
            // Ensure campaigns are up to date
            syncCampaigns(marketingChannelId);
            // Fetch only yesterday's metrics
            syncMetricsForDate(marketingChannelId, yesterday, yesterday);
            log.info("Finished daily Bing Ads sync for date {}", yesterday);
        } catch (Exception ex) {
            log.error("Error during scheduled Bing Ads sync for date {}", yesterday, ex);
        }
    }

    /**
     * Fetches all campaigns from Bing Ads and persists or updates them in the DB.
     */
    @Transactional
    public void syncCampaigns(int marketingChannelId) throws com.microsoft.bingads.v13.campaignmanagement.ApiFaultDetail_Exception, com.microsoft.bingads.v13.campaignmanagement.AdApiFaultDetail_Exception {
        MarketingChannel channel = channelRepository.findById(marketingChannelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + marketingChannelId));

        List<com.microsoft.bingads.v13.campaignmanagement.Campaign> svcCampaigns = getAllCampaigns(authorizationData);

        for (com.microsoft.bingads.v13.campaignmanagement.Campaign svc : svcCampaigns) {
            Optional<Campaign> existing = campaignRepository
                    .findByMarketingChannel_IdAndCampaignId(channel.getId(), String.valueOf(svc.getId()));

            Campaign entity = existing.orElseGet(() -> Campaign.builder()
                    .marketingChannel(channel)
                    .campaignId(String.valueOf(svc.getId()))
                    .name(svc.getName())
                    .status(svc.getStatus().value().toUpperCase())
                    .build());

            campaignRepository.save(entity);
        }
    }

    /**
     * Downloads a full history campaign performance report, parses it, and saves metrics.
     */
    public void syncAllMetrics(int marketingChannelId) throws ExecutionException, InterruptedException {
        // Download report for entire range
        LocalDate start = LocalDate.of(2000, 1, 1);
        LocalDate end = LocalDate.now().minusDays(1);

        //Additionally, call dailyBingAdsSync scheduled task for daily updates
        syncMetricsForDate(marketingChannelId, start, end);
    }

    @Transactional
    public void syncMetricsForDate(int marketingChannelId, LocalDate startDate, LocalDate endDate)
            throws ExecutionException, InterruptedException {
        MarketingChannel channel = channelRepository.findById(marketingChannelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + marketingChannelId));

        File csv = downloadCampaignPerformanceReport(authorizationData, startDate, endDate);

        try (Stream<String> lines = Files.lines(csv.toPath())) {
            lines.skip(11).forEach(line -> {
                String[] cols = line.split(",");
                if (cols.length != 9) {
                    log.warn("Skipping malformed line: {}", line);
                    return;
                }

                LocalDate statsDate = LocalDate.parse(cols[0].replace("\"", ""));
                String svcCampId = cols[1].replace("\"", "");
                int impressions = Integer.parseInt(cols[2].replace("\"", ""));
                int clicks = Integer.parseInt(cols[3].replace("\"", ""));
                BigDecimal spend = new BigDecimal(cols[4].replace("\"", ""));
                BigDecimal ctr = new BigDecimal(
                        cols[5].replace("\"", "").replace("%", "").trim().isEmpty()
                                ? "0" : cols[5].replace("\"", "").replace("%", "").trim()
                );
                BigDecimal avgCpc = new BigDecimal(cols[6].replace("\"", ""));
                int conversions = Integer.parseInt(cols[7].replace("\"", ""));
                BigDecimal convRate = new BigDecimal(
                        cols[8].replace("\"", "").replace("%", "").trim().isEmpty()
                                ? "0" : cols[8].replace("\"", "").replace("%", "").trim()
                );

                Campaign camp = campaignRepository
                        .findByMarketingChannel_IdAndCampaignId(channel.getId(), svcCampId)
                        .orElseThrow(() -> new IllegalStateException("Unknown campaign: " + svcCampId));

                //TODO found a way to get the value for: cost_per_conversion, conversion_value, value_per_conversion, roas
                CampaignMetric metric = CampaignMetric.builder()
                        .campaign(camp)
                        .statsDate(statsDate)
                        .impressions(impressions)
                        .clicks(clicks)
                        .cost(spend)
                        .ctr(ctr)
                        .avgCpc(avgCpc)
                        .conversions(conversions)
                        .conversionRate(convRate)
                        .build();

                metricRepository.save(metric);
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse report: " + csv.getAbsolutePath(), e);
        }
    }

    /**
     * Helper: fetch all campaigns via CampaignManagement API
     */
    private List<com.microsoft.bingads.v13.campaignmanagement.Campaign> getAllCampaigns(AuthorizationData auth) throws com.microsoft.bingads.v13.campaignmanagement.ApiFaultDetail_Exception, com.microsoft.bingads.v13.campaignmanagement.AdApiFaultDetail_Exception {
        ServiceClient<ICampaignManagementService> svc =
                new ServiceClient<>(auth, ICampaignManagementService.class);

        GetCampaignsByAccountIdRequest req = new GetCampaignsByAccountIdRequest();
        req.setAccountId(auth.getAccountId());
        req.setCampaignType(Arrays.asList(CampaignType.SEARCH, CampaignType.DYNAMIC_SEARCH_ADS));

        GetCampaignsByAccountIdResponse resp = svc.getService().getCampaignsByAccountId(req);
        return resp.getCampaigns().getCampaigns();
    }

    /**
     * Helper: download a CampaignPerformance report for a custom date range
     */
    private File downloadCampaignPerformanceReport(
            AuthorizationData auth,
            LocalDate customStart,
            LocalDate customEnd
    ) throws ExecutionException, InterruptedException {
        ReportingServiceManager mgr = new ReportingServiceManager(auth, ApiEnvironment.PRODUCTION);
        CampaignPerformanceReportRequest req = new CampaignPerformanceReportRequest();
        req.setFormat(ReportFormat.CSV);
        req.setReportName("AllCampaignStats");
        req.setAggregation(ReportAggregation.DAILY);

        AccountThroughCampaignReportScope scope = new AccountThroughCampaignReportScope();
        ArrayOflong aIds = new ArrayOflong();
        aIds.getLongs().add(auth.getAccountId());
        scope.setAccountIds(aIds);
        req.setScope(scope);

        ReportTime time = getReportTime(customStart, customEnd);
        req.setTime(time);

        ArrayOfCampaignPerformanceReportColumn cols = new ArrayOfCampaignPerformanceReportColumn();
        cols.getCampaignPerformanceReportColumns().addAll(Arrays.asList(
                CampaignPerformanceReportColumn.TIME_PERIOD,
                CampaignPerformanceReportColumn.CAMPAIGN_ID,
                CampaignPerformanceReportColumn.IMPRESSIONS,
                CampaignPerformanceReportColumn.CLICKS,
                CampaignPerformanceReportColumn.SPEND,
                CampaignPerformanceReportColumn.CTR,
                CampaignPerformanceReportColumn.AVERAGE_CPC,
                CampaignPerformanceReportColumn.CONVERSIONS,
                CampaignPerformanceReportColumn.CONVERSION_RATE
        ));
        req.setColumns(cols);

        String tmp = System.getProperty("java.io.tmpdir");
        File tmpDir = new File(tmp, "bingReports");
        tmpDir.mkdirs();  // ensure it exists

        ReportingDownloadParameters dl = new ReportingDownloadParameters();
        dl.setReportRequest(req);
        dl.setResultFileDirectory(tmpDir);
        dl.setResultFileName("campaign_report.csv");
        dl.setOverwriteResultFile(true);

        File reportFile = mgr.downloadFileAsync(dl, null).get();
        log.info("Report saved: {}", reportFile.getAbsolutePath());
        return reportFile;
    }

    private static ReportTime getReportTime(LocalDate customStart, LocalDate customEnd) {
        ReportTime time = new ReportTime();
        Date start = new Date();
        start.setDay(customStart.getDayOfMonth());
        start.setMonth(customStart.getMonthValue());
        start.setYear(customStart.getYear());
        Date end = new Date();
        end.setDay(customEnd.getDayOfMonth());
        end.setMonth(customEnd.getMonthValue());
        end.setYear(customEnd.getYear());
        time.setCustomDateRangeStart(start);
        time.setCustomDateRangeEnd(end);
        return time;
    }
}

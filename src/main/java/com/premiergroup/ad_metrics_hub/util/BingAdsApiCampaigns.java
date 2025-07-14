package com.premiergroup.ad_metrics_hub.util;

import com.microsoft.bingads.ApiEnvironment;
import com.microsoft.bingads.AuthorizationData;
import com.microsoft.bingads.OAuthWebAuthCodeGrant;
import com.microsoft.bingads.ServiceClient;
import com.microsoft.bingads.v13.campaignmanagement.*;
import com.microsoft.bingads.v13.campaignmanagement.ApiFaultDetail_Exception;
import com.microsoft.bingads.v13.customermanagement.*;
import com.microsoft.bingads.v13.customermanagement.AdApiFaultDetail_Exception;
import com.microsoft.bingads.v13.reporting.*;
import com.microsoft.bingads.v13.reporting.ArrayOflong;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Log4j2
public class BingAdsApiCampaigns {

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    private static final String CLIENT_ID = dotenv.get("BINGADS_CLIENT_ID");
    private static final String CLIENT_SECRET = dotenv.get("BINGADS_CLIENT_SECRET");
    private static final String DEVELOPER_TOKEN = dotenv.get("BINGADS_DEVELOPER_TOKEN");
    private static final String REFRESH_TOKEN = dotenv.get("BINGADS_REFRESH_TOKEN");
    private static final String REDIRECT_URI = dotenv.get("BINGADS_REDIRECT_URI");
    private static final long ACCOUNT_ID = Long.parseLong(dotenv.get("BINGADS_ACCOUNT_ID"));
    private static final long CUSTOMER_ID = Long.parseLong(dotenv.get("BINGADS_CUSTOMER_ID"));

    public static void main(String[] args) throws ExecutionException, InterruptedException, ApiFaultDetail_Exception, com.microsoft.bingads.v13.campaignmanagement.AdApiFaultDetail_Exception {

        System.out.println("=== Bing Ads API ===\n");

        // Cargar AuthorizationData
        AuthorizationData authorizationData = getAuthorizationData();
        //User user = getUserDetails(authorizationData);

        List<Campaign> campaigns = getAllCampaigns(authorizationData);
        campaigns.forEach(c ->
                System.out.printf("ID: %d, Name: %s, Status: %s%n",
                        c.getId(), c.getName(), c.getStatus())
        );

        LocalDate earliest = LocalDate.of(2000, 1, 1);
        LocalDate today = LocalDate.now();
        getStatsCampaignWithDateRange(authorizationData, "src/main/resources/", earliest, today, "campaigns.csv");

//        getStatsCampaignWithReportTimePeriod(authorizationData, "src/main/resources/", ReportTimePeriod.THIS_MONTH, "campaigns.csv");
    }

    private static AuthorizationData getAuthorizationData() {
        try {
            // Use OAuthWebAuthCodeGrant to handle OAuth flow
            OAuthWebAuthCodeGrant oAuth = new OAuthWebAuthCodeGrant(CLIENT_ID, CLIENT_SECRET, new URL(REDIRECT_URI));

            //Use refresh token to get access and refresh tokens
            oAuth.requestAccessAndRefreshTokens(REFRESH_TOKEN);

            // Configure AuthorizationData with OAuth tokens
            AuthorizationData authorizationData = new AuthorizationData();
            authorizationData.setDeveloperToken(DEVELOPER_TOKEN);
            authorizationData.setCustomerId(CUSTOMER_ID);
            authorizationData.setAccountId(ACCOUNT_ID);
            authorizationData.setAuthentication(oAuth);

            return authorizationData;
        } catch (MalformedURLException e) {
            log.error("Error al configurar la URL de redirección: {}", e.getMessage());
            return null;
        }
    }

    private static User getUserDetails(AuthorizationData authorizationData) {

        try {
            ServiceClient<ICustomerManagementService> CustomerService = new ServiceClient<>(
                    authorizationData,
                    ICustomerManagementService.class);

            Long userId = null;
            final GetUserRequest getUserRequest = new GetUserRequest();
            getUserRequest.setUserId(userId);
            // If you updated the authorization data such as account ID or you want to call a new operation,
            // you must call getService to set the latest request headers.
            // As a best practice you should use getService when calling any service operation.
            User user = CustomerService.getService().getUser(getUserRequest).getUser();
            log.info("Usuario obtenido: {}", user.getUserName());

            return user;

        } catch (AdApiFaultDetail_Exception | ApiFault_Exception e) {
            log.error("Error al obtener el usuario: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Error inesperado: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Downloads a Campaign Performance Report for the requested time period and saves it to the specified directory.
     */
    public static void getStatsCampaignWithReportTimePeriod(
            AuthorizationData authorizationData,
            String outputDir,
            ReportTimePeriod reportTimePeriod,
            String fileName) throws ExecutionException, InterruptedException {

        // 1) Initialize the ReportingServiceManager with your auth data
        ReportingServiceManager reportingManager = new ReportingServiceManager(authorizationData, ApiEnvironment.PRODUCTION);

        // 2) Build the report request
        CampaignPerformanceReportRequest reportRequest = new CampaignPerformanceReportRequest();
        reportRequest.setFormat(ReportFormat.CSV);
        reportRequest.setReportName("TopCampaignsByCost");
        reportRequest.setAggregation(ReportAggregation.DAILY);

        // Scope: only your account
        AccountThroughCampaignReportScope scope = new AccountThroughCampaignReportScope();

        // Create and populate the ArrayOflong
        ArrayOflong accountIds = new ArrayOflong();
        accountIds.getLongs().add(authorizationData.getAccountId());

        // Attach it to the scope
        scope.setAccountIds(accountIds);
        reportRequest.setScope(scope);

        // Time: predefined
        // 1. Create a ReportTime container
        ReportTime time = new ReportTime();
        time.setPredefinedTime(reportTimePeriod);
        reportRequest.setTime(time);


        // Columns to include
        ArrayOfCampaignPerformanceReportColumn cols = new ArrayOfCampaignPerformanceReportColumn();
        cols.getCampaignPerformanceReportColumns().addAll(Arrays.asList(
                CampaignPerformanceReportColumn.TIME_PERIOD,
                CampaignPerformanceReportColumn.CAMPAIGN_NAME,
                CampaignPerformanceReportColumn.IMPRESSIONS,
                CampaignPerformanceReportColumn.CLICKS,
                CampaignPerformanceReportColumn.SPEND,
                CampaignPerformanceReportColumn.CTR,
                CampaignPerformanceReportColumn.AVERAGE_CPC,
                CampaignPerformanceReportColumn.CONVERSIONS,
                CampaignPerformanceReportColumn.CONVERSION_RATE
        ));
        reportRequest.setColumns(cols);

        // 3) Configure download parameters
        ReportingDownloadParameters dlParams = new ReportingDownloadParameters();
        dlParams.setReportRequest(reportRequest);
        dlParams.setResultFileDirectory(new File(outputDir));
        dlParams.setResultFileName(fileName);
        dlParams.setOverwriteResultFile(true);

        // 4) Kick off the download (submits, polls, then writes file locally)
        Future<File> downloadFuture = reportingManager.downloadFileAsync(dlParams, null);
        File reportFile = downloadFuture.get();

        log.info("✅ Report saved to: {}", reportFile.getAbsolutePath());
    }

    /**
     * Downloads a Campaign Performance Report for the requested time period and saves it to the specified directory.
     */
    public static void getStatsCampaignWithDateRange(
            AuthorizationData authData,
            String outputDir,
            LocalDate customStart,
            LocalDate customEnd,
            String fileName) throws ExecutionException, InterruptedException {

        ReportingServiceManager mgr = new ReportingServiceManager(authData, ApiEnvironment.PRODUCTION);

        CampaignPerformanceReportRequest req = new CampaignPerformanceReportRequest();
        req.setFormat(ReportFormat.CSV);
        req.setReportName("AllCampaignStats");
        req.setAggregation(ReportAggregation.DAILY);

        // Scope: your account
        AccountThroughCampaignReportScope scope = new AccountThroughCampaignReportScope();
        ArrayOflong acctIds = new ArrayOflong();
        acctIds.getLongs().add(authData.getAccountId());
        scope.setAccountIds(acctIds);
        req.setScope(scope);

        // Custom date range
        ReportTime time = new ReportTime();
        com.microsoft.bingads.v13.reporting.Date start = new com.microsoft.bingads.v13.reporting.Date();
        start.setDay(customStart.getDayOfMonth());
        start.setMonth(customStart.getMonthValue());
        start.setYear(customStart.getYear());
        com.microsoft.bingads.v13.reporting.Date end = new com.microsoft.bingads.v13.reporting.Date();
        end.setDay(customEnd.getDayOfMonth());
        end.setMonth(customEnd.getMonthValue());
        end.setYear(customEnd.getYear());
        time.setCustomDateRangeStart(start);
        time.setCustomDateRangeEnd(end);
        req.setTime(time);

        // Columns…
        ArrayOfCampaignPerformanceReportColumn cols = new ArrayOfCampaignPerformanceReportColumn();
        cols.getCampaignPerformanceReportColumns().addAll(Arrays.asList(
                CampaignPerformanceReportColumn.TIME_PERIOD,
                CampaignPerformanceReportColumn.CAMPAIGN_NAME,
                CampaignPerformanceReportColumn.IMPRESSIONS,
                CampaignPerformanceReportColumn.CLICKS,
                CampaignPerformanceReportColumn.SPEND,
                CampaignPerformanceReportColumn.CTR,
                CampaignPerformanceReportColumn.AVERAGE_CPC,
                CampaignPerformanceReportColumn.CONVERSIONS,
                CampaignPerformanceReportColumn.CONVERSION_RATE
        ));
        req.setColumns(cols);

        ReportingDownloadParameters dl = new ReportingDownloadParameters();
        dl.setReportRequest(req);
        dl.setResultFileDirectory(new File(outputDir));
        dl.setResultFileName(fileName);
        dl.setOverwriteResultFile(true);

        Future<File> future = mgr.downloadFileAsync(dl, null);
        File csv = future.get();
        log.info("✅ Full-history report saved to: {}", csv.getAbsolutePath());
    }

    /**
     * Returns all campaigns for the configured account.
     */
    private static List<Campaign> getAllCampaigns(AuthorizationData authorizationData) throws ApiFaultDetail_Exception, com.microsoft.bingads.v13.campaignmanagement.AdApiFaultDetail_Exception {
        // Create a Campaign Management service client
        ServiceClient<ICampaignManagementService> campaignService =
                new ServiceClient<>(authorizationData, ICampaignManagementService.class);

        // Build the request
        GetCampaignsByAccountIdRequest request = new GetCampaignsByAccountIdRequest();
        request.setAccountId(authorizationData.getAccountId());
        // You can filter by type; here we grab search & content campaigns
        request.setCampaignType(Arrays.asList(CampaignType.SEARCH, CampaignType.DYNAMIC_SEARCH_ADS));

        // Call the service
        GetCampaignsByAccountIdResponse response =
                campaignService.getService().getCampaignsByAccountId(request);

        // Unwrap and return the list
        return response.getCampaigns().getCampaigns();
    }
}

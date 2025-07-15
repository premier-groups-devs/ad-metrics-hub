package com.premiergroup.ad_metrics_hub.config;

import com.microsoft.bingads.AuthorizationData;
import com.microsoft.bingads.OAuthWebAuthCodeGrant;
import com.premiergroup.ad_metrics_hub.AdMetricsHubApplication;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URL;

@Configuration
@Log4j2
public class BingAdsConfig {

    private static final String CLIENT_ID = AdMetricsHubApplication.dotenv.get("BINGADS_CLIENT_ID");
    private static final String CLIENT_SECRET = AdMetricsHubApplication.dotenv.get("BINGADS_CLIENT_SECRET");
    private static final String REDIRECT_URI = AdMetricsHubApplication.dotenv.get("BINGADS_REDIRECT_URI");
    private static final String REFRESH_TOKEN = AdMetricsHubApplication.dotenv.get("BINGADS_REFRESH_TOKEN");
    private static final String DEVELOPER_TOKEN = AdMetricsHubApplication.dotenv.get("BINGADS_DEVELOPER_TOKEN");
    private static final long CUSTOMER_ID = Long.parseLong(AdMetricsHubApplication.dotenv.get("BINGADS_CUSTOMER_ID"));
    private static final long ACCOUNT_ID = Long.parseLong(AdMetricsHubApplication.dotenv.get("BINGADS_ACCOUNT_ID"));

    @Bean
    public AuthorizationData getAuthorizationData() {
        try {
            OAuthWebAuthCodeGrant oauth = new OAuthWebAuthCodeGrant(CLIENT_ID, CLIENT_SECRET, new URL(REDIRECT_URI));
            oauth.requestAccessAndRefreshTokens(REFRESH_TOKEN);
            AuthorizationData auth = new AuthorizationData();
            auth.setDeveloperToken(DEVELOPER_TOKEN);
            auth.setCustomerId(CUSTOMER_ID);
            auth.setAccountId(ACCOUNT_ID);
            auth.setAuthentication(oauth);
            return auth;
        } catch (MalformedURLException e) {
            log.error("Invalid redirect URI", e);
            throw new IllegalStateException(e);
        }
    }
}

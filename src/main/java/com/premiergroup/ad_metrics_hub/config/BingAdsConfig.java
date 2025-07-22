package com.premiergroup.ad_metrics_hub.config;

import com.microsoft.bingads.AuthorizationData;
import com.microsoft.bingads.OAuthWebAuthCodeGrant;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URL;

@Configuration(proxyBeanMethods = false)
@Log4j2
public class BingAdsConfig {

    @Value("${bingads.client-id}")
    private String clientId;

    @Value("${bingads.client-secret}")
    private String clientSecret;

    @Value("${bingads.redirect-uri}")
    private String redirectUri;

    @Value("${bingads.refresh-token}")
    private String refreshToken;

    @Value("${bingads.developer-token}")
    private String developerToken;

    @Value("${bingads.customer-id}")
    private long customerId;

    @Value("${bingads.account-id}")
    private long accountId;

    @Bean
    public AuthorizationData getAuthorizationData() {
        try {
            OAuthWebAuthCodeGrant oauth = new OAuthWebAuthCodeGrant(clientId, clientSecret, new URL(redirectUri));
            oauth.requestAccessAndRefreshTokens(refreshToken);
            AuthorizationData auth = new AuthorizationData();
            auth.setDeveloperToken(developerToken);
            auth.setCustomerId(customerId);
            auth.setAccountId(accountId);
            auth.setAuthentication(oauth);
            return auth;
        } catch (MalformedURLException e) {
            log.error("Invalid redirect URI", e);
            throw new IllegalStateException(e);
        }
    }
}

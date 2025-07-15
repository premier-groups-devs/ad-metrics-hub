package com.premiergroup.ad_metrics_hub.config;

import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.premiergroup.ad_metrics_hub.AdMetricsHubApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;

@Configuration
public class GoogleAdsConfig {

    private static final String DEVELOPER_TOKEN = AdMetricsHubApplication.dotenv.get("GOOGLE_ADS_DEVELOPER_TOKEN");
    private static final String CREDENTIALS_PATH = AdMetricsHubApplication.dotenv.get("GOOGLE_CREDENTIALS_PATH");
    private static final long MCC_CUSTOMER_ID = Long.parseLong(AdMetricsHubApplication.dotenv.get("GOOGLE_ADS_MCC_CUSTOMER_ID"));
    public static final long CUSTOMER_ID = Long.parseLong(AdMetricsHubApplication.dotenv.get("GOOGLE_ADS_CUSTOMER_ID"));

    @Bean
    public GoogleAdsClient getGoogleAdsClientWithServiceAccount() throws IOException {
        // Cargar credenciales de la cuenta de servicio desde el archivo JSON
        ServiceAccountCredentials serviceAccountCredentials =
                (ServiceAccountCredentials) ServiceAccountCredentials.fromStream(
                                new FileInputStream(CREDENTIALS_PATH))
                        .createScoped(Collections.singleton("https://www.googleapis.com/auth/adwords"));

        // Construir GoogleAdsClient con credenciales de la cuenta de servicio usando variables de .env
        return GoogleAdsClient.newBuilder()
                .setCredentials(serviceAccountCredentials)
                .setDeveloperToken(DEVELOPER_TOKEN)
                .setLoginCustomerId(MCC_CUSTOMER_ID)
                .build();
    }
}

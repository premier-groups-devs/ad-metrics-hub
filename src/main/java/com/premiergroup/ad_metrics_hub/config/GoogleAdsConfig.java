package com.premiergroup.ad_metrics_hub.config;

import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Configuration(proxyBeanMethods = false)
public class GoogleAdsConfig {

//    private static final String DEVELOPER_TOKEN = AdMetricsHubApplication.dotenv.get("GOOGLE_ADS_DEVELOPER_TOKEN");
//    private static final String CREDENTIALS_PATH = AdMetricsHubApplication.dotenv.get("GOOGLE_CREDENTIALS_PATH");
//    private static final long MCC_CUSTOMER_ID = Long.parseLong(AdMetricsHubApplication.dotenv.get("GOOGLE_ADS_MCC_CUSTOMER_ID"));
//    public static final long CUSTOMER_ID = Long.parseLong(AdMetricsHubApplication.dotenv.get("GOOGLE_ADS_CUSTOMER_ID"));

    @Value("${google.ads.developer-token}")
    private String developerToken;

//    @Value("${google.ads.credentials-path}")
//    private String credentialsPath;

    @Value("${google.ads.credentials-json}")
    private String credentialsJson;

    @Value("${google.ads.mcc-customer-id}")
    private long mccCustomerId;

//    @Bean
//    public GoogleAdsClient getGoogleAdsClientWithServiceAccount() throws IOException {
//        // Cargar credenciales de la cuenta de servicio desde el archivo JSON
//        ServiceAccountCredentials serviceAccountCredentials =
//                (ServiceAccountCredentials) ServiceAccountCredentials.fromStream(
//                                new FileInputStream(credentialsPath))
//                        .createScoped(Collections.singleton("https://www.googleapis.com/auth/adwords"));
//
//        // Construir GoogleAdsClient con credenciales de la cuenta de servicio usando variables de .env
//        return GoogleAdsClient.newBuilder()
//                .setCredentials(serviceAccountCredentials)
//                .setDeveloperToken(developerToken)
//                .setLoginCustomerId(mccCustomerId)
//                .build();
//    }

    @Bean
    public GoogleAdsClient googleAdsClient() throws IOException {

        // turn JSON string into a Stream
        try (InputStream in = new ByteArrayInputStream(
                credentialsJson.getBytes(StandardCharsets.UTF_8))) {

            ServiceAccountCredentials serviceAccountCredentials =
                    (ServiceAccountCredentials) ServiceAccountCredentials
                            .fromStream(in)
                            .createScoped(
                                    Collections.singleton("https://www.googleapis.com/auth/adwords"));

            // Construir GoogleAdsClient con credenciales de la cuenta de servicio usando variables de .env
            return GoogleAdsClient.newBuilder()
                    .setCredentials(serviceAccountCredentials)
                    .setDeveloperToken(developerToken)
                    .setLoginCustomerId(mccCustomerId)
                    .build();
        }
    }
}

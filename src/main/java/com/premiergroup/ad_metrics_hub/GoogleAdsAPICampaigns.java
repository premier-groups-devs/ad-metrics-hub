package com.premiergroup.ad_metrics_hub;

import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v20.errors.GoogleAdsError;
import com.google.ads.googleads.v20.errors.GoogleAdsException;
import com.google.ads.googleads.v20.services.GoogleAdsRow;
import com.google.ads.googleads.v20.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v20.services.SearchGoogleAdsStreamRequest;
import com.google.ads.googleads.v20.services.SearchGoogleAdsStreamResponse;
import com.google.api.gax.rpc.ServerStream;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.premiergroup.ad_metrics_hub.enums.DateFilter;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Obtiene el rendimiento de las campañas activas en todas las cuentas gestionadas
 * usando autenticación con cuenta de servicio.
 */
public class GoogleAdsAPICampaigns {

    // Cargar variables de entorno usando dotenv
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    private static final String DEVELOPER_TOKEN = dotenv.get("GOOGLE_ADS_DEVELOPER_TOKEN");
    private static final String CREDENTIALS_PATH = dotenv.get("GOOGLE_CREDENTIALS_PATH");
    private static final long MCC_CUSTOMER_ID = Long.parseLong(dotenv.get("GOOGLE_ADS_MCC_CUSTOMER_ID", "9335572227"));

    public static void main(String[] args) {
        GoogleAdsAPICampaigns reporter = new GoogleAdsAPICampaigns();

        GoogleAdsClient googleAdsClient = null;
        try {
            googleAdsClient = reporter.createGoogleAdsClientWithServiceAccount();
        } catch (IOException ioe) {
            System.err.printf("Error al crear GoogleAdsClient. Excepción: %s%n", ioe);
            System.exit(1);
        }

        try {
            // Usar el ID de MCC desde el archivo .env
            reporter.reportAllManagedAccountsCampaigns(googleAdsClient);
        } catch (GoogleAdsException gae) {
            System.err.printf(
                    "Request ID %s falló debido a GoogleAdsException. Errores:%n",
                    gae.getRequestId());
            int i = 0;
            for (GoogleAdsError googleAdsError : gae.getGoogleAdsFailure().getErrorsList()) {
                System.err.printf("  Error %d: %s%n", i++, googleAdsError);
            }
            System.exit(1);
        }
    }

    private GoogleAdsClient createGoogleAdsClientWithServiceAccount() throws IOException {
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

    /**
     * Lista todas las cuentas gestionadas y reporta el rendimiento de sus campañas.
     *
     * @param googleAdsClient el cliente de Google Ads API
     * @throws GoogleAdsException si la solicitud API falla con uno o más errores
     */
    private void reportAllManagedAccountsCampaigns(GoogleAdsClient googleAdsClient) {
        try (GoogleAdsServiceClient googleAdsServiceClient =
                     googleAdsClient.getLatestVersion().createGoogleAdsServiceClient()) {

            System.out.println("Listando cuentas gestionadas para MCC ID: " + GoogleAdsAPICampaigns.MCC_CUSTOMER_ID);

            // Consulta para obtener las cuentas administradas
            String query = "SELECT customer_client.id, customer_client.descriptive_name, customer_client.status " +
                    "FROM customer_client WHERE customer_client.status = 'ENABLED' " +
                    "AND customer_client.manager = FALSE";

            // Construye la petición
            SearchGoogleAdsStreamRequest request =
                    SearchGoogleAdsStreamRequest.newBuilder()
                            .setCustomerId(Long.toString(GoogleAdsAPICampaigns.MCC_CUSTOMER_ID))
                            .setQuery(query)
                            .build();

            // Ejecuta la consulta
            ServerStream<SearchGoogleAdsStreamResponse> stream =
                    googleAdsServiceClient.searchStreamCallable().call(request);

            boolean hasAccounts = false;

            // Itera a través de los resultados
            for (SearchGoogleAdsStreamResponse response : stream) {
                for (GoogleAdsRow googleAdsRow : response.getResultsList()) {
                    hasAccounts = true;
                    long accountId = googleAdsRow.getCustomerClient().getId();
                    String accountName = googleAdsRow.getCustomerClient().getDescriptiveName();

                    System.out.println("==================================================");
                    System.out.println("📊 Account Name: " + accountName);
                    System.out.println("• Account ID: " + accountId);   // 3034914162
                    System.out.println("==================================================");

                    // Consulta el rendimiento de las campañas para esta cuenta
                    reportCampaignPerformance(googleAdsClient, accountId, DateFilter.THIS_MONTH);
                    System.out.println("\n\n");
                }
            }

            if (!hasAccounts) {
                System.out.println("No se encontraron cuentas gestionadas activas para esta cuenta MCC.");
            }
        }
    }

    /**
     * Reporta el rendimiento de las campañas para una cuenta específica.
     */
    private void reportCampaignPerformance(GoogleAdsClient googleAdsClient, long customerId, DateFilter filterDateEnum) {
        try (GoogleAdsServiceClient googleAdsServiceClient =
                     googleAdsClient.getLatestVersion().createGoogleAdsServiceClient()) {

            // Obtener el primer y último día del mes actual
            LocalDate today = LocalDate.now();
            LocalDate firstDayOfMonth = today.withDayOfMonth(1);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String startDate = firstDayOfMonth.format(formatter);
            String endDate = today.format(formatter);

            // Construir la consulta GAQL para obtener el rendimiento de las campañas
            String query =
                    "SELECT campaign.id, campaign.name, campaign.status, "
                            + "segments.date, "                                // ← include the daily segment
                            + "metrics.clicks, metrics.impressions, metrics.cost_micros, "
                            + "metrics.ctr, metrics.average_cpc, metrics.conversions, "
                            + "metrics.cost_per_conversion, metrics.all_conversions, "
                            + "metrics.all_conversions_value, metrics.value_per_conversion "
                            + "FROM campaign "
                            + "WHERE campaign.status = 'ENABLED' "
                            + "AND segments.date DURING " + filterDateEnum
                            + " ORDER BY segments.date"; //, metrics.cost_micros DESC";  // ← sort by date

            // Construir la solicitud
            SearchGoogleAdsStreamRequest request =
                    SearchGoogleAdsStreamRequest.newBuilder()
                            .setCustomerId(Long.toString(customerId))
                            .setQuery(query)
                            .build();

            // Ejecutar la consulta
            ServerStream<SearchGoogleAdsStreamResponse> stream =
                    googleAdsServiceClient.searchStreamCallable().call(request);

            // Almacenar todos los resultados para ordenarlos
            List<GoogleAdsRow> rows = new ArrayList<>();
            for (SearchGoogleAdsStreamResponse response : stream) {
                rows.addAll(response.getResultsList());
            }

//            // Ordenar por costo descendente
//            rows.sort((row1, row2) -> Long.compare(row2.getMetrics().getCostMicros(),
//                    row1.getMetrics().getCostMicros()));

            // Mostrar los resultados
            for (int i = 0; i < rows.size(); i++) {
                GoogleAdsRow row = rows.get(i);
                String date = row.getSegments().getDate();  // e.g. "2025-07-14"

                long campaignId = row.getCampaign().getId();
                String name = row.getCampaign().getName();
                String status = row.getCampaign().getStatus().name();

                long clicks = row.getMetrics().getClicks();
                long impressions = row.getMetrics().getImpressions();
                double cost = row.getMetrics().getCostMicros() / 1_000_000.0;
                double ctr = row.getMetrics().getCtr();
                double avgCpc = row.getMetrics().getAverageCpc() / 1_000_000.0;

                double conversions = row.getMetrics().getConversions();
//                double convRate = row.getMetrics().getConversionRate();
                double costPerConv = row.getMetrics().getCostPerConversion() / 1_000_000.0;

                double allConversions = row.getMetrics().getAllConversions();
                double allConvValue = row.getMetrics().getAllConversionsValue();
                double valuePerConv = row.getMetrics().getValuePerConversion();

                // Calcular ROAS
                String roas = cost > 0 ? String.format("%.2f", allConvValue / cost) : "N/A";

                System.out.println("--------------------------------------------------");
                System.out.println("📅 Date       : " + date);
                System.out.println("📌 Campaign " + (i + 1) + ": " + name);
                System.out.println("• Campaign ID        : " + campaignId);
                System.out.println("• Status             : " + status);
                System.out.println("• Impressions        : " + impressions);
                System.out.println("• Clicks             : " + clicks);
                System.out.println("• CTR                : " + String.format("%.2f%%", ctr * 100));
                System.out.println("• Avg CPC            : $" + String.format("%.2f", avgCpc));
                System.out.println("• Cost               : $" + String.format("%.2f", cost));
                System.out.println("• Conversions        : " + String.format("%.2f", conversions));
//                System.out.println("• Conv Rate          : " + String.format("%.2f%%", convRate * 100));
                System.out.println("• Cost / Conversion  : $" + String.format("%.2f", costPerConv));
                System.out.println("• All Conversions    : " + String.format("%.2f", allConversions));
                System.out.println("• All Conv Value     : $" + String.format("%.2f", allConvValue));
                System.out.println("• Value / Conv       : $" + String.format("%.2f", valuePerConv));
                System.out.println("• ROAS (Value/Cost)  : " + roas);
            }

            System.out.println("✅ Total Campaigns Reported: " + rows.size());

        } catch (GoogleAdsException gae) {
            System.err.println("Error al consultar el rendimiento de las campañas para cliente ID " +
                    customerId + ": " + gae.getMessage());
            // Mostrar los detalles del error pero continuar con las otras cuentas
            for (GoogleAdsError error : gae.getGoogleAdsFailure().getErrorsList()) {
                System.err.println("  - " + error.getMessage());
            }
        }
    }
}

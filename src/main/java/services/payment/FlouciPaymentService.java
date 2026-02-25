package services.payment;

import utils.payment.PaymentConfig;
import utils.payment.SimpleJson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Intégration Flouci — paiement mobile tunisien, mode Sandbox.
 *
 * <p>Flow :</p>
 * <ol>
 *     <li>{@link #generatePayment(double)} → lien de paiement + payment_id</li>
 *     <li>Ouvrir le lien dans un WebView JavaFX</li>
 *     <li>Détecter redirect success_url → {@link #verifyPayment(String)}</li>
 * </ol>
 *
 * <p>En sandbox, Flouci affiche une page de test permettant de valider
 * ou refuser le paiement sans débiter de vrai argent.</p>
 */
public class FlouciPaymentService {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Résultat de la génération d'un paiement Flouci.
     *
     * @param link      URL de la page de paiement Flouci
     * @param paymentId Identifiant Flouci du paiement (pour vérification)
     */
    public record FlouciPayment(String link, String paymentId) {}

    /**
     * Génère un paiement Flouci.
     *
     * @param amountTND Montant en TND (sera converti en millimes : × 1000)
     * @return lien de paiement + payment_id
     * @throws Exception en cas d'erreur réseau ou API
     */
    public FlouciPayment generatePayment(double amountTND) throws Exception {
        int amountMillimes = (int) Math.round(amountTND * 1000);
        String trackingId  = UUID.randomUUID().toString();

        String jsonBody = SimpleJson.buildObject(
                "app_token",              PaymentConfig.FLOUCI_APP_TOKEN,
                "app_secret",             PaymentConfig.FLOUCI_APP_SECRET,
                "amount",                 amountMillimes,
                "accept_card",            "true",
                "session_timeout_secs",   1200,
                "success_link",           PaymentConfig.FLOUCI_SUCCESS_URL,
                "fail_link",              PaymentConfig.FLOUCI_FAIL_URL,
                "developer_tracking_id",  trackingId
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PaymentConfig.FLOUCI_API_BASE + "/generate_payment"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(20))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 200) {
            String link      = SimpleJson.extractString(resp.body(), "link");
            String paymentId = SimpleJson.extractString(resp.body(), "payment_id");
            if (link != null && paymentId != null) {
                return new FlouciPayment(link, paymentId);
            }
            throw new RuntimeException("Réponse Flouci incomplète (link ou payment_id manquant).");
        }

        throw new RuntimeException("Flouci " + resp.statusCode() + " : "
                + resp.body().substring(0, Math.min(200, resp.body().length())));
    }

    /**
     * Vérifie le statut d'un paiement Flouci.
     *
     * @param paymentId Identifiant retourné par {@link #generatePayment}
     * @return true si le paiement est confirmé (statut SUCCESS)
     */
    public boolean verifyPayment(String paymentId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PaymentConfig.FLOUCI_API_BASE + "/verify_payment/" + paymentId))
                .header("apppublic", PaymentConfig.FLOUCI_APP_TOKEN)
                .header("appsecret", PaymentConfig.FLOUCI_APP_SECRET)
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 200) {
            String status = SimpleJson.extractString(resp.body(), "status");
            return "SUCCESS".equalsIgnoreCase(status);
        }
        return false;
    }
}

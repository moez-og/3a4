package services.payment;

import utils.payment.PaymentConfig;
import utils.payment.SimpleJson;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Intégration Stripe Checkout — mode TEST.
 *
 * <p>Crée une Session Checkout Stripe et renvoie l'URL de paiement.
 * L'URL s'ouvre dans un WebView JavaFX ; quand Stripe redirige vers
 * la success_url (fintokhrej.local/payment/success), le paiement est confirmé.</p>
 *
 * <p>Cartes de test Stripe :</p>
 * <ul>
 *     <li>4242 4242 4242 4242 — Succès</li>
 *     <li>4000 0000 0000 3220 — 3D Secure</li>
 *     <li>4000 0000 0000 0002 — Refusée</li>
 * </ul>
 */
public class StripePaymentService {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Crée une session Stripe Checkout.
     *
     * @param productName Libellé affiché (ex: "Concert Jazz × 3 tickets")
     * @param amountTND   Montant total en TND
     * @param quantity    Quantité affichée (généralement 1, le prix est déjà le total)
     * @return URL de la page Stripe Checkout
     * @throws Exception en cas d'erreur réseau ou API
     */
    public String createCheckoutSession(String productName, double amountTND, int quantity)
            throws Exception {

        // TND → EUR → centimes (Stripe attend le montant en plus petite unité)
        long amountCents = Math.round(amountTND * PaymentConfig.TND_TO_EUR * 100);
        if (amountCents < 50) amountCents = 50; // minimum Stripe : 0,50 €

        Map<String, String> params = new LinkedHashMap<>();
        params.put("payment_method_types[0]", "card");
        params.put("line_items[0][price_data][currency]", PaymentConfig.STRIPE_CURRENCY);
        params.put("line_items[0][price_data][unit_amount]", String.valueOf(amountCents));
        params.put("line_items[0][price_data][product_data][name]", productName);
        params.put("line_items[0][quantity]", String.valueOf(quantity));
        params.put("mode", "payment");
        params.put("success_url",
                PaymentConfig.STRIPE_SUCCESS_URL + "?session_id={CHECKOUT_SESSION_ID}");
        params.put("cancel_url", PaymentConfig.STRIPE_CANCEL_URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PaymentConfig.STRIPE_API_BASE + "/checkout/sessions"))
                .header("Authorization", "Bearer " + PaymentConfig.STRIPE_SECRET_KEY)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(params)))
                .timeout(Duration.ofSeconds(20))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 200) {
            String url = SimpleJson.extractString(resp.body(), "url");
            if (url != null) return url;
            throw new RuntimeException("Réponse Stripe sans URL de checkout.");
        }

        String msg = SimpleJson.extractString(resp.body(), "message");
        throw new RuntimeException("Stripe " + resp.statusCode()
                + " : " + (msg != null ? msg : resp.body().substring(0, Math.min(200, resp.body().length()))));
    }

    /**
     * Vérifie le statut d'une session Checkout.
     *
     * @return "paid", "unpaid", ou "no_payment_required"
     */
    public String getSessionPaymentStatus(String sessionId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PaymentConfig.STRIPE_API_BASE + "/checkout/sessions/" + sessionId))
                .header("Authorization", "Bearer " + PaymentConfig.STRIPE_SECRET_KEY)
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 200) {
            return SimpleJson.extractString(resp.body(), "payment_status");
        }
        return null;
    }

    // ── helpers ──

    private String encodeForm(Map<String, String> params) {
        StringJoiner sj = new StringJoiner("&");
        params.forEach((k, v) -> sj.add(
                URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return sj.toString();
    }
}

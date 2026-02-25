package services.payment;

import utils.payment.PaymentConfig;
import utils.payment.SimpleJson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Paiement en crypto-monnaie (Bitcoin / Ethereum).
 *
 * <p>Utilise l'API CoinGecko (100 % gratuite, aucune clé requise)
 * pour obtenir les taux de change en temps réel.</p>
 *
 * <p>Flow :</p>
 * <ol>
 *     <li>{@link #getQuote(double)} → taux BTC/ETH + adresses wallet</li>
 *     <li>Afficher l'adresse + montant exact à l'utilisateur</li>
 *     <li>L'utilisateur envoie les fonds et confirme manuellement</li>
 * </ol>
 *
 * <p>Note : la vérification on-chain automatique nécessiterait un nœud complet
 * ou un service comme Blockstream/Etherscan. Pour un projet académique, la
 * confirmation manuelle est suffisante.</p>
 */
public class CryptoPaymentService {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Devis crypto : montants en BTC/ETH pour un prix TND donné.
     */
    public record CryptoQuote(
            double btcAmount,
            double ethAmount,
            double usdAmount,
            double btcPriceUSD,
            double ethPriceUSD,
            String btcAddress,
            String ethAddress
    ) {}

    /**
     * Obtient un devis crypto pour un montant en TND.
     *
     * @param amountTND Prix total en dinars tunisiens
     * @return Devis avec montants BTC, ETH, adresses et taux
     * @throws Exception en cas d'erreur réseau ou API
     */
    public CryptoQuote getQuote(double amountTND) throws Exception {
        double amountUSD = amountTND * PaymentConfig.TND_TO_USD;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PaymentConfig.COINGECKO_API
                        + "/simple/price?ids=bitcoin,ethereum&vs_currencies=usd"))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 200) {
            double btcUSD = SimpleJson.extractNestedDouble(resp.body(), "bitcoin", "usd");
            double ethUSD = SimpleJson.extractNestedDouble(resp.body(), "ethereum", "usd");

            if (btcUSD <= 0 || ethUSD <= 0) {
                throw new RuntimeException("Taux CoinGecko invalides (BTC="
                        + btcUSD + ", ETH=" + ethUSD + ").");
            }

            double btcAmount = amountUSD / btcUSD;
            double ethAmount = amountUSD / ethUSD;

            return new CryptoQuote(
                    btcAmount, ethAmount, amountUSD,
                    btcUSD, ethUSD,
                    PaymentConfig.CRYPTO_BTC_ADDRESS,
                    PaymentConfig.CRYPTO_ETH_ADDRESS
            );
        }

        // Fallback : taux approximatifs (si CoinGecko est down)
        if (resp.statusCode() == 429) {
            return getFallbackQuote(amountUSD);
        }

        throw new RuntimeException("CoinGecko " + resp.statusCode());
    }

    /**
     * Devis de secours si l'API est indisponible.
     * Taux approximatifs hardcodés — à jour au moment du développement.
     */
    public CryptoQuote getFallbackQuote(double amountUSD) {
        double btcUSD = 95_000;  // ≈ prix BTC 2025-2026
        double ethUSD = 3_200;   // ≈ prix ETH 2025-2026
        return new CryptoQuote(
                amountUSD / btcUSD, amountUSD / ethUSD,
                amountUSD, btcUSD, ethUSD,
                PaymentConfig.CRYPTO_BTC_ADDRESS,
                PaymentConfig.CRYPTO_ETH_ADDRESS
        );
    }
}

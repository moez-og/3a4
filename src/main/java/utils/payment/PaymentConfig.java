package utils.payment;

/**
 * Configuration centralisée des API de paiement.
 *
 * ⚠️ MODE TEST / SANDBOX — Aucun vrai argent n'est débité.
 *
 * ── STRIPE (test mode) :
 *      1. Créez un compte gratuit sur https://dashboard.stripe.com/register
 *      2. Allez dans Developers → API Keys
 *      3. Copiez la "Secret key" (commence par sk_test_...)
 *      4. Collez-la dans STRIPE_SECRET_KEY ci-dessous
 *      Cartes de test : 4242 4242 4242 4242 (succès) | 4000 0000 0000 0002 (refusée)
 *
 * ── FLOUCI (sandbox) :
 *      1. Créez un compte développeur sur https://developers.flouci.com
 *      2. Récupérez votre app_token et app_secret (sandbox)
 *      3. Collez-les dans FLOUCI_APP_TOKEN et FLOUCI_APP_SECRET
 *
 * ── CRYPTO (CoinGecko) :
 *      100% gratuit, aucune clé nécessaire.
 *      Remplacez les adresses wallet par les vôtres.
 *
 * Quand les clés sont à leur valeur par défaut ("VOTRE_..."), le système
 * bascule automatiquement en mode simulation (aucun appel API).
 */
public final class PaymentConfig {
    private PaymentConfig() {}

    // ═══════════════════════════════════════════════════════════
    //  STRIPE  (https://stripe.com — mode test gratuit)
    // ═══════════════════════════════════════════════════════════
    public static final String STRIPE_SECRET_KEY  = "sk_test_VOTRE_CLE_STRIPE_ICI";
    public static final String STRIPE_API_BASE    = "https://api.stripe.com/v1";
    public static final String STRIPE_SUCCESS_URL = "https://fintokhrej.local/payment/success";
    public static final String STRIPE_CANCEL_URL  = "https://fintokhrej.local/payment/cancel";
    public static final String STRIPE_CURRENCY    = "eur";
    /** Taux de conversion approximatif TND → EUR (pour le mode test) */
    public static final double TND_TO_EUR         = 0.30;

    // ═══════════════════════════════════════════════════════════
    //  FLOUCI  (https://developers.flouci.com — sandbox gratuit)
    // ═══════════════════════════════════════════════════════════
    public static final String FLOUCI_APP_TOKEN   = "VOTRE_TOKEN_FLOUCI_ICI";
    public static final String FLOUCI_APP_SECRET  = "VOTRE_SECRET_FLOUCI_ICI";
    public static final String FLOUCI_API_BASE    = "https://developers.flouci.com/api";
    public static final String FLOUCI_SUCCESS_URL = "https://fintokhrej.local/flouci/success";
    public static final String FLOUCI_FAIL_URL    = "https://fintokhrej.local/flouci/fail";

    // ═══════════════════════════════════════════════════════════
    //  CRYPTO  (CoinGecko API — 100% gratuit, 0 clé)
    // ═══════════════════════════════════════════════════════════
    public static final String COINGECKO_API      = "https://api.coingecko.com/api/v3";
    /** Adresse Bitcoin de réception (remplacez par la vôtre) */
    public static final String CRYPTO_BTC_ADDRESS = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh";
    /** Adresse Ethereum de réception (remplacez par la vôtre) */
    public static final String CRYPTO_ETH_ADDRESS = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
    /** Taux de conversion approximatif TND → USD */
    public static final double TND_TO_USD         = 0.32;

    // ═══════════════════════════════════════════════════════════
    //  HELPERS : détection du mode (API réelle vs simulation)
    // ═══════════════════════════════════════════════════════════

    /** @return true si une vraie clé Stripe est configurée */
    public static boolean isStripeConfigured() {
        return STRIPE_SECRET_KEY != null
                && !STRIPE_SECRET_KEY.isBlank()
                && !STRIPE_SECRET_KEY.contains("VOTRE_CLE");
    }

    /** @return true si de vrais identifiants Flouci sont configurés */
    public static boolean isFlouciConfigured() {
        return FLOUCI_APP_TOKEN != null
                && !FLOUCI_APP_TOKEN.isBlank()
                && !FLOUCI_APP_TOKEN.contains("VOTRE_TOKEN");
    }
}

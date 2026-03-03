package utils.ui;

/**
 * Simple in-memory context for passing the selected offer/lieu between front views.
 *
 * This is intentionally minimal: values are consumed (read+cleared) to avoid stale state.
 */
public final class FrontOfferContext {

    private static Integer selectedLieuId;
    private static Integer selectedOffreId;

    private FrontOfferContext() {
    }

    public static void setSelectedLieuId(Integer lieuId) {
        selectedLieuId = lieuId;
    }

    public static Integer consumeSelectedLieuId() {
        Integer v = selectedLieuId;
        selectedLieuId = null;
        return v;
    }

    public static void setSelectedOffreId(Integer offreId) {
        selectedOffreId = offreId;
    }

    public static Integer consumeSelectedOffreId() {
        Integer v = selectedOffreId;
        selectedOffreId = null;
        return v;
    }
}

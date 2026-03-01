package utils.ui;

public final class FrontOfferContext {
    private FrontOfferContext() {}

    private static Integer selectedLieuId;
    private static Integer selectedOffreId;

    public static void setSelectedLieuId(Integer id) {
        selectedLieuId = id;
    }

    public static Integer consumeSelectedLieuId() {
        Integer tmp = selectedLieuId;
        selectedLieuId = null;
        return tmp;
    }

    public static void setSelectedOffreId(Integer id) {
        selectedOffreId = id;
    }

    public static Integer consumeSelectedOffreId() {
        Integer tmp = selectedOffreId;
        selectedOffreId = null;
        return tmp;
    }
}

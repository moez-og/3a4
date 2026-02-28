package utils.ui;

public final class ViewPaths {
    private ViewPaths() {}

    public static final String LOGIN = "/fxml/common/auth/Login.fxml";

    // Front shell + pages
    public static final String FRONT_SHELL = "/fxml/front/shell/FrontDashboard.fxml";
    public static final String FRONT_HOME = "/fxml/front/Home/FrontHome.fxml"; // ✅ correction (Home ≠ home)
    public static final String FRONT_SORTIES = "/fxml/front/sorties/SortiesView.fxml";
    public static final String FRONT_SORTIE_DETAILS = "/fxml/front/sorties/SortieDetailsView.fxml"; // ✅ AJOUT
    public static final String FRONT_LIEUX = "/fxml/front/lieux/LieuxView.fxml";
    public static final String FRONT_LIEU_DETAILS = "/fxml/front/lieux/LieuDetailsView.fxml"; // ✅ AJOUT
    public static final String FRONT_NEURO_MODE   = "/fxml/front/lieux/NeuroModeView.fxml";   // ✅ Mode neurodiversité
    public static final String FRONT_OFFRES = "/fxml/front/offres/OffresView.fxml";
    public static final String FRONT_EVENEMENTS = "/fxml/front/evenements/Evenementsview.fxml";
    public static final String FRONT_EVENEMENT_DETAILS = "/fxml/front/evenements/EvenementsDetailsView.fxml";
    public static final String FRONT_PROFIL = "/fxml/front/profil/ProfilView.fxml";
    public static final String FRONT_HELP = "/fxml/front/help/HelpView.fxml";
    public static final String FRONT_CHATBOT = "/fxml/front/lieux/ChatbotView.fxml";

    // Gamification
    public static final String FRONT_GAMIFICATION = "/fxml/front/gamification/GamificationView.fxml";

    // Back shell (si besoin)
    public static final String BACK_SHELL = "/fxml/back/shell/BackDashboard.fxml";
}
package analytics;

/**
 * Enregistrement immuable : un favori enrichi avec les données user.
 * Source unique pour tous les calculs Stream / statistiques.
 */
public record UserFavoriRecord(
    int    userId,
    int    lieuId,
    String lieuNom,
    String categorie,
    String ville,
    Double budgetMin,
    Double budgetMax,
    int    age,
    String ageSlice,
    String role,
    String dateAjout
) {
    public double budgetMoyen() {
        if (budgetMin == null && budgetMax == null) return 0.0;
        if (budgetMin != null && budgetMax != null) return (budgetMin + budgetMax) / 2.0;
        return budgetMin != null ? budgetMin : budgetMax;
    }

    public String budgetSlice() {
        double b = budgetMoyen();
        if (b == 0)   return "Gratuit";
        if (b < 20)   return "< 20 TND";
        if (b <= 80)  return "20-80 TND";
        return "> 80 TND";
    }

    /** Calcule la tranche d'âge standard */
    public static String computeAgeSlice(int age) {
        if (age <= 0)  return "Inconnu";
        if (age < 18)  return "< 18";
        if (age < 25)  return "18-24";
        if (age < 35)  return "25-34";
        if (age < 50)  return "35-49";
        return "50+";
    }
}

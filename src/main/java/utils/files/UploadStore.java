package utils.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Stocke les images uploadées dans un dossier stable (hors resources),
 * puis retourne un chemin (String) à mettre dans image_url.
 */
public final class UploadStore {

    private UploadStore() {}

    public static String saveSortieImage(File sourceFile) {
        if (sourceFile == null) return null;
        if (!sourceFile.exists()) return null;

        try {
            Path base = Paths.get(System.getProperty("user.home"), ".fintokhrej", "uploads", "sorties");
            Files.createDirectories(base);

            String ext = getExtension(sourceFile.getName());
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String name = "sortie_" + ts + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

            Path dest = base.resolve(name);
            Files.copy(sourceFile.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            return dest.toAbsolutePath().toString();

        } catch (IOException e) {
            throw new RuntimeException("UploadStore.saveSortieImage: " + e.getMessage(), e);
        }
    }

    private static String getExtension(String fileName) {
        if (fileName == null) return "";
        int i = fileName.lastIndexOf('.');
        if (i < 0) return "";
        String ext = fileName.substring(i).toLowerCase();
        // sécurité minimaliste
        if (ext.length() > 10) return "";
        return ext;
    }
}

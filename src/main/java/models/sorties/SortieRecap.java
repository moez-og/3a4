package models.sorties;

import java.time.LocalDateTime;

public class SortieRecap {
    private int id;
    private int sortieId;
    private String videoPath;
    private LocalDateTime generatedAt;
    private int version;
    private String versionLabel;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSortieId() { return sortieId; }
    public void setSortieId(int sortieId) { this.sortieId = sortieId; }

    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getVersionLabel() { return versionLabel; }
    public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }
}

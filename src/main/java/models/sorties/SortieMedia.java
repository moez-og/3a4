package models.sorties;

import java.time.LocalDateTime;

public class SortieMedia {
    private int id;
    private int sortieId;
    private int userId;
    private String authorName;
    private String filePath;
    private String mediaType; // IMAGE | VIDEO
    private LocalDateTime uploadedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSortieId() { return sortieId; }
    public void setSortieId(int sortieId) { this.sortieId = sortieId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public boolean isImage() { return "IMAGE".equalsIgnoreCase(mediaType); }
    public boolean isVideo() { return "VIDEO".equalsIgnoreCase(mediaType); }
}

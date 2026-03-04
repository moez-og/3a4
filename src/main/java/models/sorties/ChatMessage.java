package models.sorties;

import java.time.LocalDateTime;

public class ChatMessage {

    private int id;
    private int annonceId;
    private int senderId;
    private String senderName;   // chargé en JOIN (non stocké en BDD)
    private String content;
    private LocalDateTime sentAt;

    // Extensions (compatibles rétro) :
    // - messageType : TEXT | POLL | SYSTEM (etc.)
    // - pollId : référence vers un sondage (si messageType == POLL)
    private String messageType = "TEXT";
    private Integer pollId;
    private String metaJson;

    public ChatMessage() {}

    public ChatMessage(int annonceId, int senderId, String content) {
        this.annonceId = annonceId;
        this.senderId  = senderId;
        this.content   = content;
    }

    public int getId()                        { return id; }
    public void setId(int id)                 { this.id = id; }

    public int getAnnonceId()                 { return annonceId; }
    public void setAnnonceId(int annonceId)   { this.annonceId = annonceId; }

    public int getSenderId()                  { return senderId; }
    public void setSenderId(int senderId)     { this.senderId = senderId; }

    public String getSenderName()             { return senderName; }
    public void setSenderName(String n)       { this.senderName = n; }

    public String getContent()                { return content; }
    public void setContent(String content)    { this.content = content; }

    public LocalDateTime getSentAt()          { return sentAt; }
    public void setSentAt(LocalDateTime t)    { this.sentAt = t; }

    public String getMessageType()            { return messageType; }
    public void setMessageType(String t)      { this.messageType = (t == null || t.isBlank()) ? "TEXT" : t; }

    public Integer getPollId()                { return pollId; }
    public void setPollId(Integer pollId)     { this.pollId = pollId; }

    public String getMetaJson()               { return metaJson; }
    public void setMetaJson(String metaJson)  { this.metaJson = metaJson; }
}
package com.doceditor.docmanager.kafka.dto;

public class NewDocumentMessage {

    private String type;
    private String userId;
    private String title;

    public NewDocumentMessage() {
    }

    public NewDocumentMessage(String userId, String title) {
        this.type = "new_document";
        this.userId = userId;
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

}

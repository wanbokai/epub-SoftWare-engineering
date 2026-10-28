package com.reader.file;

/**
 * 秒传预检响应。
 */
public class UploadCheckResponse {

    private boolean exists;
    private String sha256;
    private Long fileId;
    private String metaTitle;
    private String metaAuthor;

    public UploadCheckResponse() {
    }

    public UploadCheckResponse(boolean exists, String sha256) {
        this.exists = exists;
        this.sha256 = sha256;
    }

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getMetaTitle() {
        return metaTitle;
    }

    public void setMetaTitle(String metaTitle) {
        this.metaTitle = metaTitle;
    }

    public String getMetaAuthor() {
        return metaAuthor;
    }

    public void setMetaAuthor(String metaAuthor) {
        this.metaAuthor = metaAuthor;
    }
}

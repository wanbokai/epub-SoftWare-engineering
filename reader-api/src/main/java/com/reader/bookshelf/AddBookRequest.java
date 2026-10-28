package com.reader.bookshelf;

/**
 * 添加书籍到书架的请求体。
 */
public class AddBookRequest {

    private Long fileId;
    private String displayTitle;

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public void setDisplayTitle(String displayTitle) {
        this.displayTitle = displayTitle;
    }
}

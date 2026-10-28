package com.reader.bookshelf;

/**
 * 更新书架条目（重命名）请求体。
 */
public class UpdateBookRequest {

    private String displayTitle;

    public String getDisplayTitle() {
        return displayTitle;
    }

    public void setDisplayTitle(String displayTitle) {
        this.displayTitle = displayTitle;
    }
}

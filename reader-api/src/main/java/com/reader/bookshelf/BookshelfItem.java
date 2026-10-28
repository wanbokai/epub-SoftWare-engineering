package com.reader.bookshelf;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户书架条目实体（对应数据库 bookshelf_item 表）。
 * 每条记录表示某用户书架中的一本书，引用一个物理文件资源。
 */
public class BookshelfItem {

    private Long id;
    private Long userId;
    private Long fileId;
    private String displayTitle;
    private String progressCfi;
    private BigDecimal progressPercent;
    private LocalDateTime lastReadAt;
    private LocalDateTime createdAt;

    // 关联查询时填充的文件元信息（非表字段）
    private Integer fileType;
    private Integer fileSize;
    private String metaTitle;
    private String metaAuthor;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public String getProgressCfi() {
        return progressCfi;
    }

    public void setProgressCfi(String progressCfi) {
        this.progressCfi = progressCfi;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(BigDecimal progressPercent) {
        this.progressPercent = progressPercent;
    }

    public LocalDateTime getLastReadAt() {
        return lastReadAt;
    }

    public void setLastReadAt(LocalDateTime lastReadAt) {
        this.lastReadAt = lastReadAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getFileType() {
        return fileType;
    }

    public void setFileType(Integer fileType) {
        this.fileType = fileType;
    }

    public Integer getFileSize() {
        return fileSize;
    }

    public void setFileSize(Integer fileSize) {
        this.fileSize = fileSize;
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

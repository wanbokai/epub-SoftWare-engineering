package com.reader.sync;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排版配置实体（对应数据库 reader_config 表）。
 * 每用户一条，通过 sync_version 做乐观锁版本比对。
 */
public class ReaderConfig {

    private Long id;
    private Long userId;
    private Integer theme;        // 1日间/2夜间/3羊皮纸/4护眼
    private Integer fontSize;     // 字号（px）
    private BigDecimal lineHeight; // 行高（倍）
    private Integer pageMargin;   // 1窄/2中/3宽
    private Integer pageMode;     // 1分页/2流式
    private Integer syncVersion;  // 乐观锁版本号
    private LocalDateTime updatedAt;

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

    public Integer getTheme() {
        return theme;
    }

    public void setTheme(Integer theme) {
        this.theme = theme;
    }

    public Integer getFontSize() {
        return fontSize;
    }

    public void setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
    }

    public BigDecimal getLineHeight() {
        return lineHeight;
    }

    public void setLineHeight(BigDecimal lineHeight) {
        this.lineHeight = lineHeight;
    }

    public Integer getPageMargin() {
        return pageMargin;
    }

    public void setPageMargin(Integer pageMargin) {
        this.pageMargin = pageMargin;
    }

    public Integer getPageMode() {
        return pageMode;
    }

    public void setPageMode(Integer pageMode) {
        this.pageMode = pageMode;
    }

    public Integer getSyncVersion() {
        return syncVersion;
    }

    public void setSyncVersion(Integer syncVersion) {
        this.syncVersion = syncVersion;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

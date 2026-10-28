package com.reader.sync;

import java.math.BigDecimal;

/**
 * 上行配置请求体。
 */
public class PushConfigRequest {

    private Integer theme;
    private Integer fontSize;
    private BigDecimal lineHeight;
    private Integer pageMargin;
    private Integer pageMode;
    private Integer syncVersion;

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
}

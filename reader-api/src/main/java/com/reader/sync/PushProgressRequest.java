package com.reader.sync;

import java.math.BigDecimal;

/**
 * 上行进度请求体。
 */
public class PushProgressRequest {

    private String cfi;
    private BigDecimal percent;
    private Integer syncVersion;

    public String getCfi() {
        return cfi;
    }

    public void setCfi(String cfi) {
        this.cfi = cfi;
    }

    public BigDecimal getPercent() {
        return percent;
    }

    public void setPercent(BigDecimal percent) {
        this.percent = percent;
    }

    public Integer getSyncVersion() {
        return syncVersion;
    }

    public void setSyncVersion(Integer syncVersion) {
        this.syncVersion = syncVersion;
    }
}

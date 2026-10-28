package com.reader.sync;

import java.math.BigDecimal;

/**
 * 冲突响应体：云端与本端摘要，供前端消解面板渲染。
 */
public class ConflictResponse {

    private ProgressSummary cloud;
    private ProgressSummary local;
    private Integer cloudVersion;
    private Integer localVersion;

    public ConflictResponse() {
    }

    public ConflictResponse(ProgressSummary cloud, ProgressSummary local,
                            Integer cloudVersion, Integer localVersion) {
        this.cloud = cloud;
        this.local = local;
        this.cloudVersion = cloudVersion;
        this.localVersion = localVersion;
    }

    public ProgressSummary getCloud() {
        return cloud;
    }

    public void setCloud(ProgressSummary cloud) {
        this.cloud = cloud;
    }

    public ProgressSummary getLocal() {
        return local;
    }

    public void setLocal(ProgressSummary local) {
        this.local = local;
    }

    public Integer getCloudVersion() {
        return cloudVersion;
    }

    public void setCloudVersion(Integer cloudVersion) {
        this.cloudVersion = cloudVersion;
    }

    public Integer getLocalVersion() {
        return localVersion;
    }

    public void setLocalVersion(Integer localVersion) {
        this.localVersion = localVersion;
    }

    /**
     * 进度摘要。
     */
    public static class ProgressSummary {
        private String cfi;
        private BigDecimal percent;

        public ProgressSummary() {
        }

        public ProgressSummary(String cfi, BigDecimal percent) {
            this.cfi = cfi;
            this.percent = percent;
        }

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
    }
}

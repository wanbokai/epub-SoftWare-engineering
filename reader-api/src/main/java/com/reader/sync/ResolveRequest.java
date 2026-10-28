package com.reader.sync;

/**
 * 冲突消解请求体。choice: cloud（保留云端）/ local（采用本机）/ lww（最后写入胜）。
 */
public class ResolveRequest {

    private String choice;

    // 选择 local 时携带本端进度
    private String cfi;
    private java.math.BigDecimal percent;

    public String getChoice() {
        return choice;
    }

    public void setChoice(String choice) {
        this.choice = choice;
    }

    public String getCfi() {
        return cfi;
    }

    public void setCfi(String cfi) {
        this.cfi = cfi;
    }

    public java.math.BigDecimal getPercent() {
        return percent;
    }

    public void setPercent(java.math.BigDecimal percent) {
        this.percent = percent;
    }
}

package com.hxj.entity;

/** 系统支持的三类业务单据（日常付款/业务付款/用印申请）。 */
public enum BusinessType {
    /** 日常付款：日常费用类付款业务，含费用报销、差旅费、招待费、借款申请等。 */
    DAILY_PAYMENT("BX"),
    /** 业务付款：应付款申请、供应商货款、工资社保等业务侧付款。 */
    BUSINESS_PAYMENT("FK"),
    /** 用印申请：用印及证照类申请，不涉及资金收付。 */
    SEAL_APPLICATION("YY");

    private final String codePrefix;

    BusinessType(String codePrefix) {
        this.codePrefix = codePrefix;
    }

    public String getCodePrefix() {
        return codePrefix;
    }

    public DocumentType toDocumentType() {
        return switch (this) {
            case DAILY_PAYMENT -> DocumentType.DAILY_APPLICATION;
            case BUSINESS_PAYMENT -> DocumentType.PAYMENT_APPLICATION;
            case SEAL_APPLICATION -> DocumentType.SEAL_APPLICATION;
        };
    }
}

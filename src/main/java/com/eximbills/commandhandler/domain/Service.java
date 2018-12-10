package com.eximbills.commandhandler.domain;

public class Service {
    private String baseUrl;
    private String trxUrl;
    private String compensatingUrl;
    private Boolean holdFlag;
    private String submitStatus;
    private String commitUrl;
    private String commitStatus;
    private String compensatingStatus;

    public Service(String baseUrl, String trxUrl, String compensatingUrl, Boolean holdFlag,
                   String commitUrl) {
        this.setBaseUrl(baseUrl);
        this.setTrxUrl(trxUrl);
        this.setCompensatingUrl(compensatingUrl);
        this.setHoldFlag(holdFlag);
        this.setCommitUrl(commitUrl);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTrxUrl() {
        return trxUrl;
    }

    public void setTrxUrl(String trxUrl) {
        this.trxUrl = trxUrl;
    }

    public String getCompensatingUrl() {
        return compensatingUrl;
    }

    public void setCompensatingUrl(String compensatingUrl) {
        this.compensatingUrl = compensatingUrl;
    }

    public Boolean getHoldFlag() {
        return holdFlag;
    }

    public void setHoldFlag(Boolean holdFlag) {
        this.holdFlag = holdFlag;
    }

    public String getSubmitStatus() {
        return submitStatus;
    }

    public void setSubmitStatus(String submitStatus) {
        this.submitStatus = submitStatus;
    }

    public String getCommitUrl() {
        return commitUrl;
    }

    public void setCommitUrl(String commitUrl) {
        this.commitUrl = commitUrl;
    }

    public String getCommitStatus() {
        return commitStatus;
    }

    public void setCommitStatus(String commitStatus) {
        this.commitStatus = commitStatus;
    }

    public String getCompensatingStatus() {
        return compensatingStatus;
    }

    public void setCompensatingStatus(String compensatingStatus) {
        this.compensatingStatus = compensatingStatus;
    }
}
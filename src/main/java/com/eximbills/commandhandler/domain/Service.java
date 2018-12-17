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
    private String retrieveUrl;
    private Boolean debitCreditFlag;
    private float amount;

    public Service(String baseUrl, String trxUrl, String compensatingUrl, Boolean holdFlag,
                   String commitUrl, String retrieveUrl, Boolean debitCreditFlag, float amount) {
        this.setBaseUrl(baseUrl);
        this.setTrxUrl(trxUrl);
        this.setCompensatingUrl(compensatingUrl);
        this.setHoldFlag(holdFlag);
        this.setCommitUrl(commitUrl);
        this.setRetrieveUrl(retrieveUrl);
        this.setDebitCreditFlag(debitCreditFlag);
        this.setAmount(amount);
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

    public String getRetrieveUrl() {
        return retrieveUrl;
    }

    public void setRetrieveUrl(String retrieveUrl) {
        this.retrieveUrl = retrieveUrl;
    }

    public Boolean getDebitCreditFlag() {
        return debitCreditFlag;
    }

    public void setDebitCreditFlag(Boolean debitCreditFlag) {
        this.debitCreditFlag = debitCreditFlag;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }
}

package com.eximbills.commandhandler.domain;

public class Balance {

    private Long id;

    private float balance;

    private String holdFlag;

    protected  Balance() {}

    public Balance(Long id, float balance, String holdFlag) {
        this.setId(id);
        this.setBalance(balance);
        this.setHoldFlag(holdFlag);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public float getBalance() {
        return balance;
    }

    public void setBalance(float balance) {
        this.balance = balance;
    }

    public String getHoldFlag() {
        return holdFlag;
    }

    public void setHoldFlag(String holdFlag) {
        this.holdFlag = holdFlag;
    }

    @Override
    public String toString() {
        return getId() + "," + getBalance() + "," + getHoldFlag();
    }
}

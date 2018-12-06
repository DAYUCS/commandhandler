package com.eximbills.commandhandler.domain;

public class Entry {

    private String id;

    private float entryAmount;

    private Balance balance;

    protected Entry() {
    }

    public Entry(String id, float entryAmount, Balance balance) {
        this.setId(id);
        this.setEntryAmount(entryAmount);
        this.setBalance(balance);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public float getEntryAmount() {
        return entryAmount;
    }

    public void setEntryAmount(float entryAmount) {
        this.entryAmount = entryAmount;
    }

    public Balance getBalance() {
        return balance;
    }

    public void setBalance(Balance balance) {
        this.balance = balance;
    }

    @Override
    public String toString() {
        return getId() + "," + getEntryAmount() + "," + getBalance().toString();
    }
}

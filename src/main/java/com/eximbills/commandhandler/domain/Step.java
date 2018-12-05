package com.eximbills.commandhandler.domain;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.io.Serializable;

@Entity
public class Step implements Serializable {

    @Id
    @Column(length = 36)
    private String id;

    @Column(length = 256)
    private String entryDescription;

    @Column(length = 32)
    private String entryStatus;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Event event;

    protected Step() {
    }

    public Step(String id, String entryDescription, String entryStatus, Event event) {
        this.setId(id);
        this.setEntryDescription(entryDescription);
        this.setEntryStatus(entryStatus);
        this.setEvent(event);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEntryDescription() {
        return entryDescription;
    }

    public void setEntryDescription(String entryDescription) {
        this.entryDescription = entryDescription;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public String getEntryStatus() {
        return entryStatus;
    }

    public void setEntryStatus(String entryStatus) {
        this.entryStatus = entryStatus;
    }

    @Override
    public String toString() {
        return getId() + "," + getEntryDescription() + "," + getEntryStatus()
                + "," + getEvent().toString();
    }


}

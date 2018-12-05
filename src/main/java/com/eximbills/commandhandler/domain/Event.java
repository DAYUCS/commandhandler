package com.eximbills.commandhandler.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;

@Entity
public class Event implements Serializable {

    @Id
    @Column(length = 36)
    private String id;

    @Column(length = 256)
    private String description;

    @Column(length = 32)
    private String status;

    protected Event() {}

    public Event(String id, String description, String status) {
        this.setId(id);
        this.setDescription(description);
        this.setStatus(status);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return getId() + "," + getDescription() + "," + getStatus();
    }
}

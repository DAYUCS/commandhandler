package com.eximbills.commandhandler.domain;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@RequiredArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "events")
public class Event {

    @Id
    private String id;

    private List<Service> serviceList = new ArrayList<>();

    private Date createdDate;

    private String eventStatus;

    private Date updatedDate;

    public Event(String id, List<Service> serviceList) {
        this.id = id;
        this.serviceList = serviceList;
        this.createdDate = new Date();
    }

}

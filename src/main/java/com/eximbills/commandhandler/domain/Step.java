package com.eximbills.commandhandler.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@RequiredArgsConstructor
@Getter
@Setter
@ToString
@Document(collection = "steps")
public class Step {

    @Id
    private String id;

    @NonNull
    private String eventId;

    @NonNull
    private String serviceId;

    @NonNull
    private String stepType;

    @NonNull
    private String stepStatus;

    @NonNull
    private Date createdDate;

    public Step (String id, String eventId, String serviceId, String stepType, String stepStatus) {
        this.id = id;
        this.eventId = eventId;
        this.serviceId = serviceId;
        this.stepType = stepType;
        this.stepStatus = stepStatus;
        this.createdDate = new Date();
    }

}

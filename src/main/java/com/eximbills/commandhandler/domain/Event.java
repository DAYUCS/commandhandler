package com.eximbills.commandhandler.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@RequiredArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
@Getter
@Setter
@ToString
@Document(collection = "events")
public class Event {

    @Id
    private String id;

    @NonNull
    private String description;

    @NonNull
    private String status;

    @NonNull
    private Date createdDate;

    public Event(String id, String description, String status) {
        this.id = id;
        this.description = description;
        this.status = status;
        this.createdDate = new Date();
    }

}

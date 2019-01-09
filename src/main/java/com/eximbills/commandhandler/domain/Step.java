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
@Document(collection = "steps")
public class Step {

    @Id
    private String id;

    @NonNull
    private String eventId;

    @NonNull
    private String entryDescription;

    @NonNull
    private String entryStatus;

    @NonNull
    private Date createdDate;

    public Step (String id, String eventId, String entryDescription, String entryStatus) {
        this.id = id;
        this.eventId = eventId;
        this.entryDescription = entryDescription;
        this.entryStatus = entryStatus;
        this.createdDate = new Date();
    }

}

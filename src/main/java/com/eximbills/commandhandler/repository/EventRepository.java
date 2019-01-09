package com.eximbills.commandhandler.repository;

import com.eximbills.commandhandler.domain.Event;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends ReactiveMongoRepository<Event, String> {
}

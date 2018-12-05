package com.eximbills.commandhandler.repository;

import com.eximbills.commandhandler.domain.Event;
import org.springframework.data.repository.CrudRepository;

public interface EventRepository extends CrudRepository<Event, String> {
}

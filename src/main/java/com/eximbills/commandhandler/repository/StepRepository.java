package com.eximbills.commandhandler.repository;

import com.eximbills.commandhandler.domain.Step;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

public interface StepRepository extends CrudRepository<Step, String> {
    Page<Step> findByEventId(String eventId, Pageable pageable);
}

package com.eximbills.commandhandler.repository;

import com.eximbills.commandhandler.domain.Step;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StepRepository extends ReactiveMongoRepository<Step, String> {
}

package com.eximbills.commandhandler;

import com.eximbills.commandhandler.domain.Balance;
import com.eximbills.commandhandler.domain.Event;
import com.eximbills.commandhandler.domain.Step;
import com.eximbills.commandhandler.repository.EventRepository;
import com.eximbills.commandhandler.repository.StepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@EnableAutoConfiguration
public class CommandHandlerApplication {

    private static final Logger logger = LoggerFactory.getLogger(CommandHandlerApplication.class);

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private StepRepository stepRepository;

    public static void main(String[] args) {
        SpringApplication.run(CommandHandlerApplication.class, args);
    }

    @RequestMapping("/")
    @ResponseBody
    @Transactional
    String home() {
        logger.debug("Request for /");

        String eventId = UUID.randomUUID().toString();
        Event event = new Event(eventId, "Test Event",
                "finished");

        String stepId = UUID.randomUUID().toString();
        Step step = new Step(stepId, "Test Step",
                "finished", event);

        eventRepository.save(event);
        stepRepository.save(step);

        eventRepository.findById(stepId).ifPresent(evt -> {
            logger.debug("Event found with findById():" + evt.toString());
        });
        stepRepository.findById(stepId).ifPresent(stp -> {
            logger.debug("Step: " + stp.toString());
        });
        return "Application listening on port 8080...";
    }

    @GetMapping("/balance/{id}")
    @ResponseBody
    @Transactional(readOnly = true)
    Mono<List> getBalanceInfo(@PathVariable("id") Long id) {
        List<Balance> balances = new ArrayList<>();

        Mono<Balance> balance1 = WebClient.create()
                .get()
                .uri("http://localhost:8081/balance/{id}", id)
                .retrieve()
                .bodyToMono(Balance.class);

        Mono<Balance> balance2 = WebClient.create()
                .get()
                .uri("http://localhost:8082/balance/{id}", id)
                .retrieve()
                .bodyToMono(Balance.class);

        /*balance1.timeout(Duration.ofSeconds(30))
                .subscribe(
                        bal -> logger.debug("Balance from Service 1: " + bal.toString()),
                        e -> logger.debug(e.getLocalizedMessage())
                );

        balance2.timeout(Duration.ofSeconds(30))
                .subscribe(
                        bal -> logger.debug("Balance from Service 2: " + bal.toString()),
                        e -> logger.debug(e.getLocalizedMessage())
                );*/

        Mono<List> var = Mono.zip(balance1, balance2)
                .map(tuple -> {
                    Balance bal1 = tuple.getT1();
                    Balance bal2 = tuple.getT2();
                    balances.add(bal1);
                    balances.add(bal2);
                    return balances;
                });
//                .timeout(Duration.ofSeconds(30))
//                .subscribe(
//                        bals -> {
//                            logger.debug("Balance record number: " + bals.size());
//                            logger.debug("Balance: " + bals.get(0).toString());
//                            logger.debug("Balance: " + bals.get(1).toString());
//                        },
//                        e -> logger.debug(e.getLocalizedMessage())
//                );

        return var;
    }

    /*
    @PutMapping("/balance/{id}/{amount}/{transactionId}")
    @ResponseBody
    @Transactional
    public Step postEntry(@PathVariable("id") Long id, @PathVariable("amount") String amount,
                          @PathVariable("transactionId") String transactionId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No event found with id=" + id));
        if (event.getStatus().equals("Y")) throw new ResourceLockedException();
        event.setDescription(event.getDescription() + amount);
        event.setStatus("Y");
        Step step = new Step(transactionId, amount, event);
        eventRepository.save(event);
        return stepRepository.save(step);
    }

    @PutMapping("/balance/{id}")
    @ResponseBody
    @Transactional
    public Event unHold(@PathVariable("id") Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No event found with id=" + id));
        event.setStatus("N");
        return eventRepository.save(event);
    }

    @PutMapping("/balance/{id}/{transactionId}")
    @ResponseBody
    @Transactional
    public Step reverseEntry(@PathVariable("id") Long id,
                             @PathVariable("transactionId") String transactionId) {
        Step step = stepRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("No step found with id=" + id));
        Event event = step.getEvent();
        event.setDescription(event.getDescription() - step.getEntryDescription());
        event.setStatus("N");
        step.setEntryDescription(0);
        eventRepository.save(event);
        return stepRepository.save(step);
    }*/

}

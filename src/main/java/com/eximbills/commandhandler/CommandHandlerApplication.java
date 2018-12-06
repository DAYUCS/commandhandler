package com.eximbills.commandhandler;

import com.eximbills.commandhandler.domain.Balance;
import com.eximbills.commandhandler.domain.Entry;
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
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
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

        return var.subscribeOn(Schedulers.elastic());  // retrieve each balance on a different thread.
    }


    @PutMapping("/balance/{id}/{amount}")
    @ResponseBody
    public String postTransaction(@PathVariable("id") Long id, @PathVariable("amount") float amount) {
        logger.debug("Request for post transaction: id " + id + ", amount " + amount);

        // Before post, write transaction info into event store
        String eventId = UUID.randomUUID().toString();
        Event event = new Event(eventId, "Post transaction with id " + id + ", amount " + amount,
                "start");

        String stepId = UUID.randomUUID().toString();
        Step step = new Step(stepId, "Post transaction with id " + id + ", amount " + amount,
                "start", event);

        eventRepository.save(event);
        stepRepository.save(step);

        // Post transaction
        List<Entry> entries = new ArrayList<>();

        Mono<Entry> entry1 = WebClient.create()
                .put()
                .uri("http://localhost:8081/balance/{id}/{amount}/{transactionId}", id, amount, eventId)
                .retrieve()
                .bodyToMono(Entry.class);

        Mono<Entry> entry2 = WebClient.create()
                .put()
                .uri("http://localhost:8082/balance/{id}/{amount}/{transactionId}", id, amount, eventId)
                .retrieve()
                .bodyToMono(Entry.class);

        Mono.zip(entry1, entry2)
                .map(tuple -> {
                    Entry ent1 = tuple.getT1();
                    Entry ent2 = tuple.getT2();
                    entries.add(ent1);
                    entries.add(ent2);
                    return entries;
                })
                .timeout(Duration.ofSeconds(30))
                .subscribeOn(Schedulers.elastic())
                .subscribe(
                        ents -> {
                            // success
                            logger.debug("There are " + ents.size() + " trx submitted successfully.");
                            logger.debug("Trx 1: " + ents.get(0).toString());
                            logger.debug("Trx 2: " + ents.get(1).toString());
                        },
                        e -> {
                            // failure
                            logger.debug(e.getMessage());
                        }
                );

        return " ";
    }

}

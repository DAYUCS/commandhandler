package com.eximbills.commandhandler;

import com.eximbills.commandhandler.domain.*;
import com.eximbills.commandhandler.repository.EventRepository;
import com.eximbills.commandhandler.repository.StepRepository;
import com.fasterxml.uuid.Generators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
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
    public Service[] postTransaction(@PathVariable("id") Long id, @PathVariable("amount") float amount) {

        logger.debug("Request for post transaction: id " + id + ", amount " + amount);

        StringBuffer message = new StringBuffer();

        // Services definition
        Service[] services =
                new Service[]{
                        new Service("http://localhost:8081/balance",
                                "/{id}/{amount}/{transactionId}",
                                "/{id}/{transactionId}",
                                true, "/{id}"),
                        new Service("http://localhost:8082/balance",
                                "/{id}/{amount}/{transactionId}",
                                "/{id}/{transactionId}",
                                true, "/{id}")};

        Flux<Service> steps = Flux.fromArray(services);

        // Before post, write transaction info into event store
        String eventId = Generators.timeBasedGenerator().generate().toString();
        Event event = new Event(eventId, "Post transaction with id " + id + ", amount " + amount,
                "start");

        String stepId = Generators.timeBasedGenerator().generate().toString();
        Step step = new Step(stepId, "Post transaction with id " + id + ", amount " + amount,
                "start", event);

        eventRepository.save(event);
        stepRepository.save(step);

        // Build Web API call of submits
        List<Mono<Entry>> entries = new ArrayList();
        steps.subscribe(stp -> {
            Mono<Entry> entry = WebClient.create()
                    .put()
                    .uri(stp.getBaseUrl() + stp.getTrxUrl(), id, amount, eventId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatus::is4xxClientError, clientResponse ->
                            Mono.error(new ResourceLockedException()))
                    .bodyToMono(Entry.class)
                    .log()
                    .doOnSuccess(p -> {
                        logger.debug("Call " + stp.getTrxUrl() + " completed with response " + p.toString());
                        stp.setSubmitStatus("success");
                        // TODO save step status into event store
                    })
                    .doOnError(ex -> {
                        logger.debug("Call " + stp.getTrxUrl() + " completed with error " + ex.getMessage());
                        stp.setSubmitStatus("error");
                        // TODO save step status into event store
                    });
            entries.add(entry);
        });

        logger.debug("Step number is " + entries.size());

        // Zip all API call
        Mono<String> all = Mono.zipDelayError(entries, values -> {
            for (int i = 0; i < values.length; i++) {
                logger.debug("Submit Trx " + i + ": " + values[i].toString());
                message.append("Submit Trx" + i + ": " + values[i] + "; ");
            }
            return message.toString();
        });

        // submit transactions
        all.timeout(Duration.ofSeconds(30))
                .subscribeOn(Schedulers.elastic())
                .subscribe(
                        val -> {
                            logger.debug("Zip value: " + val);
                            // Commitment here
                            Flux<Service> commitSteps = Flux.fromArray(services)
                                    .filter(service -> service.getHoldFlag());
                            List<Mono<Balance>> balances = new ArrayList();
                            commitSteps.subscribe(commitStep -> {
                                Mono<Balance> balance = WebClient.create()
                                        .put()
                                        .uri(commitStep.getBaseUrl() + commitStep.getCommitUrl(), id)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .retrieve()
                                        .onStatus(HttpStatus::is4xxClientError, clientResponse ->
                                                Mono.error(new ResourceLockedException()))
                                        .bodyToMono(Balance.class)
                                        .log()
                                        .doOnSuccess(p -> {
                                            logger.debug("Call " + commitStep.getCommitUrl()
                                                    + " completed with response " + p.toString());
                                            commitStep.setCommitStatus("success");
                                            // TODO save submit step status into event store
                                        })
                                        .doOnError(ex -> {
                                            logger.debug("Call " + commitStep.getCommitUrl()
                                                    + " completed with error " + ex.getMessage());
                                            commitStep.setCommitStatus("error");
                                            // TODO save submit step status into event store
                                        });
                                balances.add(balance);
                            });
                            logger.debug("Record number to commit: " + balances.size());
                            Mono.zipDelayError(balances, values -> {
                                for (int i = 0; i < values.length; i++) {
                                    logger.debug("Commit Trx " + i + ": " + values[i].toString());
                                    message.append("Commit Trx" + i + ": " + values[i] + "; ");
                                }
                                return message.toString();
                            }).timeout(Duration.ofSeconds(30))
                                    .subscribeOn(Schedulers.elastic()).subscribe(v -> {
                                        //TODO save commit step status into event store
                                    },
                                    e -> {
                                        //TODO save commit step status into event store
                                    });
                        },
                        err -> {
                            logger.debug("Zip error: " + err.getMessage());
                            // TODO Compensating here
                            Flux<Service> compensatingSteps = Flux.fromArray(services)
                                    .filter(service -> service.getSubmitStatus().equals("success"));
                            List<Mono<Entry>> entriesCompensating = new ArrayList();
                            compensatingSteps.subscribe(compensatingStep -> {
                                Mono<Entry> entryCompensating = WebClient.create()
                                        .put()
                                        .uri(compensatingStep.getBaseUrl() + compensatingStep.getCompensatingUrl(), id, eventId)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .retrieve()
                                        .onStatus(HttpStatus::is4xxClientError, clientResponse ->
                                                Mono.error(new ResourceLockedException()))
                                        .bodyToMono(Entry.class)
                                        .log()
                                        .doOnSuccess(p -> {
                                            logger.debug("Call " + compensatingStep.getCompensatingUrl()
                                                    + " completed with response " + p.toString());
                                            compensatingStep.setCompensatingStatus("success");
                                            // TODO save compensating step status into event store
                                        })
                                        .doOnError(ex -> {
                                            logger.debug("Call " + compensatingStep.getCompensatingUrl()
                                                    + " completed with error " + ex.getMessage());
                                            compensatingStep.setCompensatingStatus("error");
                                            // TODO save compensating step status into event store
                                        });
                                entriesCompensating.add(entryCompensating);
                            });
                            logger.debug("Record number to compensating: " + entriesCompensating.size());
                            Mono.zipDelayError(entriesCompensating, values -> {
                                for (int i = 0; i < values.length; i++) {
                                    logger.debug("Compensating Trx " + i + ": " + values[i].toString());
                                    message.append("Compensating Trx" + i + ": " + values[i] + "; ");
                                }
                                return message.toString();
                            }).timeout(Duration.ofSeconds(30))
                                    .subscribeOn(Schedulers.elastic()).subscribe(v -> {
                                        //TODO save compensating step status into event store
                                    },
                                    e -> {
                                        //TODO save compensating step status into event store
                                    });
                        }
                );

        return services;
    }

}

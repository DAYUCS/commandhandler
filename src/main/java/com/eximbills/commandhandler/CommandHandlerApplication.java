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

import java.util.ArrayList;
import java.util.Arrays;
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
    Mono<String> getBalanceInfo(@PathVariable("id") Long id) {
        logger.debug("Request for get balances with Id " + id);

        //Service definition
        Service[] services =
                new Service[]{
                        new Service("http://localhost:8081/balance",
                                "/{id}/{amount}/{transactionId}",
                                "/{id}/{transactionId}",
                                true, "/{id}", "/{id}"),
                        new Service("http://localhost:8082/balance",
                                "/{id}/{amount}/{transactionId}",
                                "/{id}/{transactionId}",
                                true, "/{id}", "/{id}")};

        StringBuffer sb = new StringBuffer();

        List<Mono<Balance>> balances = new ArrayList();
        Flux.fromArray(services)
                .parallel()
                .subscribe(stp -> {
                    Mono<Balance> balance = WebClient.create()
                            .get()
                            .uri(stp.getBaseUrl() + stp.getRetrieveUrl(), id)
                            .retrieve()
                            .bodyToMono(Balance.class)
                            .log();
                    balances.add(balance);
                });

        return Mono.zipDelayError(balances, values -> {
            for (int i = 0; i < values.length; i++) {
                sb.append("Balance " + i + " - " + values[i] + "; ");
            }
            return sb.toString();
        });
    }


    @PutMapping("/balance/{id}/{amount}")
    @ResponseBody
    public Mono<String> postTransaction(@PathVariable("id") Long id, @PathVariable("amount") float amount) {
        logger.debug("Request for post transaction: id " + id + ", amount " + amount);

        //Service definition
        Service[] services =
                new Service[]{
                        new Service("http://localhost:8081/balance",
                                "/{id}/{amount}/{transactionId}",
                                "/{id}/{transactionId}",
                                true, "/{id}", "/{id}"),
                        new Service("http://localhost:8082/balance",
                                "/{id}/{amount}/{transactionId}",
                                "/{id}/{transactionId}",
                                true, "/{id}", "/{id}")};

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
        Flux.fromArray(services)
                .parallel()
                .subscribe(stp -> {
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

        logger.debug("There are " + entries.size() + " transactions to be submitted");

        // Merge all API call
        return Mono.whenDelayError(entries)
                .subscribeOn(Schedulers.parallel())
                .onErrorResume(ex -> {
                    logger.debug("Catch submit zip exception: " + ex.getMessage());
                    return Mono.empty();
                })
                .then(Mono.defer(() -> commitOrCompensating(services, id, eventId)))
                .then(Mono.defer(() -> constructReturnMessage(services)));
    }

    private Mono<Void> commitOrCompensating(Service[] services, Long id, String eventId) {

        int errors = Arrays.stream(services)
                .filter(service -> service.getSubmitStatus().equals("error"))
                .toArray()
                .length;
        logger.debug("There are/is " + errors + " service call failed.");

        if (errors == 0) {
            // Commit hold trx here
            List<Mono<Balance>> balances = new ArrayList();

            Flux.fromArray(services)
                    .parallel()
                    .filter(service -> service.getHoldFlag())
                    .subscribe(commitStep -> {
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
                                    // TODO save commit step status into event store
                                })
                                .doOnError(ex -> {
                                    logger.debug("Call " + commitStep.getCommitUrl()
                                            + " completed with error " + ex.getMessage());
                                    commitStep.setCommitStatus("error");
                                    // TODO save commit step status into event store
                                });
                        balances.add(balance);
                    });

            logger.debug("Record number to commit: " + balances.size());
            return Mono.whenDelayError(balances)
                    .subscribeOn(Schedulers.parallel())
                    .onErrorResume(ex -> {
                        logger.debug("Catch commit zip exception: " + ex.getMessage());
                        return Mono.empty();
                    });
        } else {
            // Compensating here
            List<Mono<Entry>> entriesCompensating = new ArrayList();
            Flux.fromArray(services)
                    .parallel()
                    .filter(service -> service.getSubmitStatus().equals("success"))
                    .subscribe(compensatingStep -> {
                        Mono<Entry> entryCompensating = WebClient.create()
                                .put()
                                .uri(compensatingStep.getBaseUrl() + compensatingStep.getCompensatingUrl(),
                                        id, eventId)
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
            return Mono.whenDelayError(entriesCompensating)
                    .subscribeOn(Schedulers.parallel())
                    .onErrorResume(ex -> {
                        logger.debug("Catch compensating zip exception: " + ex.getMessage());
                        return Mono.empty();
                    });
        }
    }

    private Mono<String> constructReturnMessage(Service[] services) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < services.length; i++) {
            sb.append("Service " + i + ": " + services[i].getBaseUrl() + " - ");
            sb.append("submit status: " + services[i].getSubmitStatus() + ", ");
            sb.append("commit status: " + services[i].getCommitStatus() + ", ");
            sb.append("compensating status: " + services[i].getCompensatingStatus() + "; ");
        }
        return Mono.just(sb.toString());
    }
}

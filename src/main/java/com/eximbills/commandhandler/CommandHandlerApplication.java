package com.eximbills.commandhandler;

import com.eximbills.commandhandler.domain.*;
import com.eximbills.commandhandler.repository.EventRepository;
import com.eximbills.commandhandler.repository.StepRepository;
import com.fasterxml.uuid.Generators;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
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
import java.util.Date;
import java.util.List;

@RestController
@EnableAutoConfiguration
@EnableReactiveMongoRepositories
public class CommandHandlerApplication extends AbstractReactiveMongoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CommandHandlerApplication.class);

    @Autowired
    private Environment environment;
    @Autowired
    private ReactiveMongoOperations reactiveMongoOperations;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private StepRepository stepRepository;

    public static void main(String[] args) {
        SpringApplication.run(CommandHandlerApplication.class, args);
    }

    @Bean
    public MongoClient reactiveMongoClient() {
        return MongoClients.create();
    }

    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate() {
        return new ReactiveMongoTemplate(reactiveMongoClient(), "eventstore");
    }

    @Override
    protected String getDatabaseName() {
        return environment.getProperty("spring.data.mongodb.database");
    }

    @RequestMapping("/")
    @ResponseBody
    @Transactional
    String home() {
        logger.debug("Request for /");
        return "Application listening on port 8080...";
    }

    @GetMapping("/balance/{id}")
    @ResponseBody
    Mono<List<Balance>> getBalanceInfo(@PathVariable("id") Long id) {
        logger.debug("Request for get balances with Id " + id);

        //Service definition
        Service[] services =
                new Service[]{
                        new Service("1", "http://localhost:8081/balance",
                                "/{id}/{amount}/{transactionId}",
                                "/{id}/{transactionId}",
                                true, "/{id}", "/{id}",
                                true, (float) 0.00),
                        new Service("2", "http://localhost:8082/balance",
                                "/{id}/{amount}/{transactionId}",
                                "/{id}/{transactionId}",
                                true, "/{id}", "/{id}",
                                false, (float) 0.00),
                        new Service("3", "http://localhost:8083/balance",
                                "/{id}/{amount}/{transactionId}",
                                "/{id}/{transactionId}",
                                true, "/{id}", "/{id}",
                                false, (float) 0.00)};

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

        List<Balance> balanceList = new ArrayList();
        return Mono.zipDelayError(balances, values -> {
            for (int i = 0; i < values.length; i++) {
                balanceList.add((Balance) values[i]);
                logger.debug("Balance " + i + " - " + values[i] + ";\n\t");
            }
            return "";
        }).subscribeOn(Schedulers.parallel()).then(Mono.defer(() -> {
            return Mono.just(balanceList);
        }));
    }

    @PostMapping(value = "/balance/{id}", consumes = "application/json")
    @ResponseBody
    public Mono<Event> postTransaction(@PathVariable("id") Long id, @RequestBody Service[] services) {
        logger.debug("Request for post transaction: id " + id);

        // Before post, write transaction info into event store
        String eventId = Generators.timeBasedGenerator().generate().toString();
        Event event = new Event(eventId, new ArrayList<Service>(Arrays.asList(services)));

        eventRepository.save(event).subscribe();

        // Build Web API call of submits
        List<Mono<Entry>> entries = new ArrayList();
        Flux.fromArray(services)
                .parallel()
                .subscribe(stp -> {
                    float trxAmount = stp.getDebitCreditFlag() ? stp.getAmount() : -stp.getAmount();
                    Mono<Entry> entry = WebClient.create()
                            .put()
                            .uri(stp.getBaseUrl() + stp.getTrxUrl(), id, trxAmount, eventId)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .onStatus(HttpStatus::is4xxClientError, clientResponse ->
                                    Mono.error(new ResourceLockedException()))
                            .bodyToMono(Entry.class)
                            .log()
                            .doOnSuccess(p -> {
                                logger.debug("Call " + stp.getTrxUrl() + " completed with response " + p.toString());
                                stp.setSubmitStatus("succeed");
                                String stepId = Generators.timeBasedGenerator().generate().toString();
                                Step step = new Step(stepId, eventId, stp.getId(), "submit", "succeed");
                                stepRepository.save(step).subscribe();
                            })
                            .doOnError(ex -> {
                                logger.debug("Call " + stp.getTrxUrl() + " completed with error " + ex.getMessage());
                                stp.setSubmitStatus("fail");
                                String stepId = Generators.timeBasedGenerator().generate().toString();
                                Step step = new Step(stepId, eventId, stp.getId(), "submit", "fail");
                                stepRepository.save(step).subscribe();
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
                .then(Mono.defer(() -> saveEventStatus(event, services)));
    }

    private Mono<Void> commitOrCompensating(Service[] services, Long id, String eventId) {

        int errors = Arrays.stream(services)
                .filter(service -> service.getSubmitStatus().equals("fail"))
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
                                    commitStep.setCommitStatus("succeed");
                                    String stepId = Generators.timeBasedGenerator().generate().toString();
                                    Step step = new Step(stepId, eventId, commitStep.getId(), "commit", "succeed");
                                    stepRepository.save(step).subscribe();
                                })
                                .doOnError(ex -> {
                                    logger.debug("Call " + commitStep.getCommitUrl()
                                            + " completed with error " + ex.getMessage());
                                    commitStep.setCommitStatus("fail");
                                    String stepId = Generators.timeBasedGenerator().generate().toString();
                                    Step step = new Step(stepId, eventId, commitStep.getId(), "commit", "fail");
                                    stepRepository.save(step).subscribe();
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
                    //.filter(service -> service.getSubmitStatus().equals("success")) //all services should be compensated
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
                                    compensatingStep.setCompensatingStatus("succeed");
                                    String stepId = Generators.timeBasedGenerator().generate().toString();
                                    Step step = new Step(stepId, eventId, compensatingStep.getId(), "compensating", "succeed");
                                    stepRepository.save(step).subscribe();
                                })
                                .doOnError(ex -> {
                                    logger.debug("Call " + compensatingStep.getCompensatingUrl()
                                            + " completed with error " + ex.getMessage());
                                    compensatingStep.setCompensatingStatus("fail");
                                    String stepId = Generators.timeBasedGenerator().generate().toString();
                                    Step step = new Step(stepId, eventId, compensatingStep.getId(), "compensating", "fail");
                                    stepRepository.save(step).subscribe();
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

    private Mono<Event> saveEventStatus(Event event, Service[] services) {
        event.setServiceList(Arrays.asList(services));
        event.setEventStatus("Completed");
        event.setUpdatedDate(new Date());
        return eventRepository.save(event);
    }
}

package com.eximbills.commandhandler.domain;

import lombok.*;

@Data
@RequiredArgsConstructor
@Getter
@Setter
public class Service {

    private String id;
    private String baseUrl;
    private String trxUrl;
    private String compensatingUrl;
    private Boolean holdFlag;
    private String submitStatus;
    private String commitUrl;
    private String commitStatus;
    private String compensatingStatus;
    private String retrieveUrl;
    private Boolean debitCreditFlag;
    private float amount;

    public Service(String id, String baseUrl, String trxUrl, String compensatingUrl, Boolean holdFlag,
                   String commitUrl, String retrieveUrl, Boolean debitCreditFlag, float amount) {
        this.id = id;
        this.baseUrl = baseUrl;
        this.trxUrl = trxUrl;
        this.compensatingUrl = compensatingUrl;
        this.holdFlag = holdFlag;
        this.commitUrl = commitUrl;
        this.retrieveUrl = retrieveUrl;
        this.debitCreditFlag = debitCreditFlag;
        this.amount = amount;
    }
}

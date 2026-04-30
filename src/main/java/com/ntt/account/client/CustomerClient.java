package com.ntt.account.client;

import com.ntt.account.model.dto.CustomerSummaryResponse;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.adapter.rxjava.RxJava3Adapter;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerClient {

  private static final String CUSTOMER_SERVICE_URL = "http://CUSTOMER-SERVICE/api/v1/customers";

  private final WebClient.Builder webClientBuilder;

  public Single<CustomerSummaryResponse> getCustomerSummary(String customerId) {
    log.debug("Consultando resumen de cliente. customerId={}", customerId);
    return RxJava3Adapter.monoToSingle(
            webClientBuilder
                .build()
                .get()
                .uri(CUSTOMER_SERVICE_URL + "/{customerId}/summary", customerId)
                .retrieve()
                .bodyToMono(CustomerSummaryResponse.class))
        .doOnSuccess(
            customer ->
                log.debug(
                    "Resumen de cliente recibido. customerId={}, status={}, type={}",
                    customerId,
                    customer.getStatus(),
                    customer.getType()))
        .doOnError(
            error ->
                log.error(
                    "Error consultando resumen de cliente. customerId={}, error={}",
                    customerId,
                    error.getMessage()));
  }
}

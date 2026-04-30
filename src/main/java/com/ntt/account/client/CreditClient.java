package com.ntt.account.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditClient {

  private final WebClient.Builder webClientBuilder;

  private static final String CREDIT_SERVICE_URL = "http://CREDIT-SERVICE/api/v1/credits";

  @CircuitBreaker(name = "creditService", fallbackMethod = "fallbackHasOverdueDebtMono")
  @TimeLimiter(name = "creditService")
  public Mono<Boolean> hasOverdueDebtMono(String customerId) {
    log.debug("Consultando deuda vencida. customerId={}", customerId);
    return webClientBuilder
        .build()
        .get()
        .uri(CREDIT_SERVICE_URL + "/customer/{customerId}/has-overdue-debt", customerId)
        .retrieve()
        .bodyToMono(Boolean.class)
        .doOnSuccess(
            hasDebt ->
                log.debug(
                    "Respuesta de deuda vencida recibida. customerId={}, hasDebt={}",
                    customerId,
                    hasDebt));
  }

  public Mono<Boolean> fallbackHasOverdueDebtMono(String customerId, Throwable t) {
    log.error(
        "Fallback ejecutado para validacion de deuda del cliente {}: {}",
        customerId,
        t.getMessage());
    return Mono.just(true);
  }

  // Adaptador nativo RxJava 3 expuesto para la capa de Servicio
  public Single<Boolean> hasOverdueDebt(String customerId) {
    return RxJava3Adapter.monoToSingle(hasOverdueDebtMono(customerId));
  }

  @CircuitBreaker(name = "creditService", fallbackMethod = "fallbackHasActiveCreditCardMono")
  @TimeLimiter(name = "creditService")
  public Mono<Boolean> hasActiveCreditCardMono(String customerId) {
    log.debug("Consultando tarjeta de credito activa. customerId={}", customerId);
    return webClientBuilder
        .build()
        .get()
        .uri(CREDIT_SERVICE_URL + "/customer/{customerId}/has-active-credit-card", customerId)
        .retrieve()
        .bodyToMono(Boolean.class)
        .doOnSuccess(
            hasCard ->
                log.debug(
                    "Respuesta de tarjeta de credito activa recibida. customerId={}, hasCard={}",
                    customerId,
                    hasCard));
  }

  public Mono<Boolean> fallbackHasActiveCreditCardMono(String customerId, Throwable t) {
    log.error(
        "Fallback ejecutado para validacion de tarjeta activa del cliente {}: {}",
        customerId,
        t.getMessage());
    return Mono.just(false);
  }

  public Single<Boolean> hasActiveCreditCard(String customerId) {
    return RxJava3Adapter.monoToSingle(hasActiveCreditCardMono(customerId));
  }
}

package com.ntt.account.controller;

import com.ntt.account.model.dto.AccountCreationRequest;
import com.ntt.account.model.dto.CustomerProductsResponse;
import com.ntt.account.model.dto.DebitCardCreationRequest;
import com.ntt.account.model.dto.LinkAccountRequest;
import com.ntt.account.model.dto.TransactionRequest;
import com.ntt.account.model.entity.Account;
import com.ntt.account.model.entity.DebitCard;
import com.ntt.account.service.AccountService;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

  private final AccountService accountService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Single<ResponseEntity<Account>> createAccount(
      @Valid @RequestBody AccountCreationRequest request) {
    log.info(
        "Solicitud recibida para crear cuenta. customerId={}, accountType={}",
        request.getCustomerId(),
        request.getAccountType());
    return accountService
        .createAccount(request)
        .doOnSuccess(
            account -> log.info("Cuenta creada exitosamente. accountId={}", account.getId()))
        .doOnError(error -> log.warn("No se pudo crear la cuenta: {}", error.getMessage()))
        .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
  }

  @GetMapping("/customer/{customerId}")
  public Single<ResponseEntity<CustomerProductsResponse>> getProductsByCustomerId(
      @PathVariable String customerId) {
    log.info("Solicitud recibida para consultar productos. customerId={}", customerId);
    return accountService
        .getProductsByCustomerId(customerId)
        .doOnSuccess(response -> log.info("Productos consultados. customerId={}", customerId))
        .doOnError(error -> log.warn("No se pudieron consultar productos: {}", error.getMessage()))
        .map(ResponseEntity::ok);
  }

  @GetMapping("/{id}/balance")
  public Single<ResponseEntity<Account>> getAccountBalance(@PathVariable String id) {
    log.info("Solicitud recibida para consultar saldo. accountId={}", id);
    return accountService
        .getAccountBalance(id)
        .doOnSuccess(account -> log.info("Saldo consultado. accountId={}", account.getId()))
        .doOnError(error -> log.warn("No se pudo consultar saldo: {}", error.getMessage()))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/{id}/deposit")
  public Single<ResponseEntity<Account>> deposit(
      @PathVariable String id, @Valid @RequestBody TransactionRequest request) {
    log.info("Solicitud recibida para deposito. accountId={}", id);
    return accountService
        .deposit(id, request)
        .doOnSuccess(account -> log.info("Deposito aplicado. accountId={}", account.getId()))
        .doOnError(error -> log.warn("No se pudo aplicar deposito: {}", error.getMessage()))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/{id}/withdraw")
  public Single<ResponseEntity<Account>> withdraw(
      @PathVariable String id, @Valid @RequestBody TransactionRequest request) {
    log.info("Solicitud recibida para retiro. accountId={}", id);
    return accountService
        .withdraw(id, request)
        .doOnSuccess(account -> log.info("Retiro aplicado. accountId={}", account.getId()))
        .doOnError(error -> log.warn("No se pudo aplicar retiro: {}", error.getMessage()))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/{id}/compensations/deposit")
  public Single<ResponseEntity<Account>> compensateDeposit(
      @PathVariable String id, @Valid @RequestBody TransactionRequest request) {
    log.info("Solicitud recibida para compensar deposito. accountId={}", id);
    return accountService
        .compensateDeposit(id, request)
        .doOnSuccess(account -> log.info("Deposito compensado. accountId={}", account.getId()))
        .doOnError(error -> log.warn("No se pudo compensar deposito: {}", error.getMessage()))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/debit-card")
  @ResponseStatus(HttpStatus.CREATED)
  public Single<ResponseEntity<DebitCard>> createDebitCard(
      @Valid @RequestBody DebitCardCreationRequest request) {
    log.info(
        "Solicitud recibida para crear tarjeta de debito. customerId={}, mainAccountId={}",
        request.getCustomerId(),
        request.getMainAccountId());
    return accountService
        .createDebitCard(request)
        .doOnSuccess(card -> log.info("Tarjeta de debito creada. cardId={}", card.getId()))
        .doOnError(error -> log.warn("No se pudo crear tarjeta de debito: {}", error.getMessage()))
        .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
  }

  @PutMapping("/debit-card/{id}/link-account")
  public Single<ResponseEntity<DebitCard>> linkAccount(
      @PathVariable String id, @Valid @RequestBody LinkAccountRequest request) {
    log.info(
        "Solicitud recibida para vincular cuenta secundaria. cardId={}, secondaryAccountId={}",
        id,
        request.getSecondaryAccountId());
    return accountService
        .linkAccount(id, request)
        .doOnSuccess(card -> log.info("Cuenta vinculada a tarjeta. cardId={}", card.getId()))
        .doOnError(error -> log.warn("No se pudo vincular cuenta: {}", error.getMessage()))
        .map(ResponseEntity::ok);
  }

  @GetMapping("/debit-cards/{cardNumber}")
  public Single<ResponseEntity<DebitCard>> getDebitCardByNumber(@PathVariable String cardNumber) {
    log.info("Solicitud recibida para consultar tarjeta de debito.");
    return accountService
        .getDebitCardByNumber(cardNumber)
        .doOnSuccess(card -> log.info("Tarjeta de debito consultada. cardId={}", card.getId()))
        .doOnError(error -> log.warn("No se pudo consultar tarjeta: {}", error.getMessage()))
        .map(ResponseEntity::ok);
  }
}

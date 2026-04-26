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
public class AccountController {

  private final AccountService accountService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Single<ResponseEntity<Account>> createAccount(
      @Valid @RequestBody AccountCreationRequest request) {
    return accountService
        .createAccount(request)
        .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
  }

  @GetMapping("/customer/{customerId}")
  public Single<ResponseEntity<CustomerProductsResponse>> getProductsByCustomerId(
      @PathVariable String customerId) {
    return accountService.getProductsByCustomerId(customerId).map(ResponseEntity::ok);
  }

  @GetMapping("/{id}/balance")
  public Single<ResponseEntity<Account>> getAccountBalance(@PathVariable String id) {
    return accountService.getAccountBalance(id).map(ResponseEntity::ok);
  }

  @PostMapping("/{id}/deposit")
  public Single<ResponseEntity<Account>> deposit(
      @PathVariable String id, @Valid @RequestBody TransactionRequest request) {
    return accountService.deposit(id, request).map(ResponseEntity::ok);
  }

  @PostMapping("/{id}/withdraw")
  public Single<ResponseEntity<Account>> withdraw(
      @PathVariable String id, @Valid @RequestBody TransactionRequest request) {
    return accountService.withdraw(id, request).map(ResponseEntity::ok);
  }

  @PostMapping("/{id}/compensations/deposit")
  public Single<ResponseEntity<Account>> compensateDeposit(
      @PathVariable String id, @Valid @RequestBody TransactionRequest request) {
    return accountService.compensateDeposit(id, request).map(ResponseEntity::ok);
  }

  @PostMapping("/debit-card")
  @ResponseStatus(HttpStatus.CREATED)
  public Single<ResponseEntity<DebitCard>> createDebitCard(
      @Valid @RequestBody DebitCardCreationRequest request) {
    return accountService
        .createDebitCard(request)
        .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
  }

  @PutMapping("/debit-card/{id}/link-account")
  public Single<ResponseEntity<DebitCard>> linkAccount(
      @PathVariable String id, @Valid @RequestBody LinkAccountRequest request) {
    return accountService.linkAccount(id, request).map(ResponseEntity::ok);
  }

  @GetMapping("/debit-cards/{cardNumber}")
  public Single<ResponseEntity<DebitCard>> getDebitCardByNumber(@PathVariable String cardNumber) {
    return accountService.getDebitCardByNumber(cardNumber).map(ResponseEntity::ok);
  }
}

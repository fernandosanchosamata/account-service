package com.ntt.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ntt.account.model.dto.AccountCreationRequest;
import com.ntt.account.model.dto.CustomerProductsResponse;
import com.ntt.account.model.dto.DebitCardCreationRequest;
import com.ntt.account.model.dto.LinkAccountRequest;
import com.ntt.account.model.dto.TransactionRequest;
import com.ntt.account.model.entity.Account;
import com.ntt.account.model.entity.DebitCard;
import com.ntt.account.model.enums.AccountType;
import com.ntt.account.service.AccountService;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

  @Mock private AccountService accountService;

  @InjectMocks private AccountController controller;

  @Test
  void createAccountReturnsCreatedResponse() {
    AccountCreationRequest request = new AccountCreationRequest();
    request.setCustomerId("customer-1");
    request.setAccountType(AccountType.SAVINGS);
    request.setInitialBalance(BigDecimal.valueOf(100));
    Account account = account();
    when(accountService.createAccount(request)).thenReturn(Single.just(account));

    var result = controller.createAccount(request).blockingGet();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody()).isEqualTo(account);
  }

  @Test
  void getAccountBalanceReturnsOkResponse() {
    Account account = account();
    when(accountService.getAccountBalance("account-1")).thenReturn(Single.just(account));

    var result = controller.getAccountBalance("account-1").blockingGet();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(account);
    verify(accountService).getAccountBalance("account-1");
  }

  @Test
  void depositDelegatesToServiceAndReturnsOkResponse() {
    TransactionRequest request = new TransactionRequest();
    request.setAmount(BigDecimal.valueOf(50));
    Account account = account();
    account.setBalance(BigDecimal.valueOf(150));
    when(accountService.deposit("account-1", request)).thenReturn(Single.just(account));

    var result = controller.deposit("account-1", request).blockingGet();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(account);
    verify(accountService).deposit("account-1", request);
  }

  @Test
  void withdrawDelegatesToServiceAndReturnsOkResponse() {
    TransactionRequest request = new TransactionRequest();
    request.setAmount(BigDecimal.valueOf(25));
    Account account = account();
    account.setBalance(BigDecimal.valueOf(75));
    when(accountService.withdraw("account-1", request)).thenReturn(Single.just(account));

    var result = controller.withdraw("account-1", request).blockingGet();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(account);
    verify(accountService).withdraw("account-1", request);
  }

  @Test
  void compensateDepositDelegatesToServiceAndReturnsOkResponse() {
    TransactionRequest request = new TransactionRequest();
    request.setAmount(BigDecimal.valueOf(40));
    Account account = account();
    account.setBalance(BigDecimal.valueOf(140));
    when(accountService.compensateDeposit("account-1", request)).thenReturn(Single.just(account));

    var result = controller.compensateDeposit("account-1", request).blockingGet();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(account);
    verify(accountService).compensateDeposit("account-1", request);
  }

  @Test
  void createDebitCardReturnsCreatedResponse() {
    DebitCardCreationRequest request = new DebitCardCreationRequest();
    request.setCustomerId("customer-1");
    request.setMainAccountId("account-1");
    request.setCardNumber("4555-6666-7777-8888");
    DebitCard card = debitCard();
    when(accountService.createDebitCard(request)).thenReturn(Single.just(card));

    var result = controller.createDebitCard(request).blockingGet();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody()).isEqualTo(card);
  }

  @Test
  void linkAccountDelegatesToServiceAndReturnsOkResponse() {
    LinkAccountRequest request = new LinkAccountRequest();
    request.setSecondaryAccountId("account-2");
    DebitCard card = debitCard();
    card.getSecondaryAccountIds().add("account-2");
    when(accountService.linkAccount("card-1", request)).thenReturn(Single.just(card));

    var result = controller.linkAccount("card-1", request).blockingGet();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(card);
    verify(accountService).linkAccount("card-1", request);
  }

  @Test
  void getDebitCardByNumberReturnsOkResponse() {
    DebitCard card = debitCard();
    when(accountService.getDebitCardByNumber("4555-6666-7777-8888")).thenReturn(Single.just(card));

    var result = controller.getDebitCardByNumber("4555-6666-7777-8888").blockingGet();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(card);
    verify(accountService).getDebitCardByNumber("4555-6666-7777-8888");
  }

  @Test
  void getProductsByCustomerIdReturnsCustomerProducts() {
    CustomerProductsResponse response =
        CustomerProductsResponse.builder()
            .customerId("customer-1")
            .accounts(List.of(account()))
            .debitCards(List.of(debitCard()))
            .build();
    when(accountService.getProductsByCustomerId("customer-1")).thenReturn(Single.just(response));

    var result = controller.getProductsByCustomerId("customer-1").blockingGet();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(response);
  }

  private Account account() {
    return Account.builder()
        .id("account-1")
        .customerId("customer-1")
        .type(AccountType.SAVINGS)
        .balance(BigDecimal.valueOf(100))
        .build();
  }

  private DebitCard debitCard() {
    return DebitCard.builder()
        .id("card-1")
        .customerId("customer-1")
        .mainAccountId("account-1")
        .cardNumber("4555-6666-7777-8888")
        .build();
  }
}

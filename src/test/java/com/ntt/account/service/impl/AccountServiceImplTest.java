package com.ntt.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ntt.account.client.CreditClient;
import com.ntt.account.client.CustomerClient;
import com.ntt.account.model.dto.AccountCreationRequest;
import com.ntt.account.model.dto.AccountRuleDefinition;
import com.ntt.account.model.dto.CustomerSummaryResponse;
import com.ntt.account.model.dto.DebitCardCreationRequest;
import com.ntt.account.model.dto.TransactionRequest;
import com.ntt.account.model.entity.Account;
import com.ntt.account.model.entity.DebitCard;
import com.ntt.account.model.enums.AccountStatus;
import com.ntt.account.model.enums.AccountType;
import com.ntt.account.repository.AccountRepository;
import com.ntt.account.repository.DebitCardRepository;
import com.ntt.account.service.AccountRuleService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

  @Mock private AccountRepository accountRepository;

  @Mock private DebitCardRepository debitCardRepository;

  @Mock private CreditClient creditClient;

  @Mock private CustomerClient customerClient;

  @Mock private AccountRuleService accountRuleService;

  private AccountServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new AccountServiceImpl(
            accountRepository,
            debitCardRepository,
            creditClient,
            customerClient,
            accountRuleService);
  }

  @Test
  void depositAddsAmountAndAppliesExtraTransactionFee() {
    Account account = activeAccount(BigDecimal.valueOf(100));
    account.setTransactionCount(1);
    account.setTransactionCycle(currentCycle());
    account.setMaxFreeTransactions(1);
    TransactionRequest request = transaction(BigDecimal.valueOf(50));
    AccountRuleDefinition rule = regularRule();
    rule.setMaxFreeTransactions(1);
    rule.setExtraTransactionFee(BigDecimal.valueOf(5));
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(rule));
    when(accountRepository.save(any(Account.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    Account result = service.deposit("account-1", request).blockingGet();

    assertThat(result.getBalance()).isEqualByComparingTo("145");
    assertThat(result.getTransactionCount()).isEqualTo(2);
    assertThat(result.getTransactionCycle()).isEqualTo(currentCycle());
    assertThat(result.getLastTransactionDate()).isEqualTo(LocalDate.now());
  }

  @Test
  void withdrawRejectsWhenBalanceIsInsufficientForAmountAndFee() {
    Account account = activeAccount(BigDecimal.valueOf(50));
    TransactionRequest request = transaction(BigDecimal.valueOf(50));
    AccountRuleDefinition rule = regularRule();
    rule.setMaxFreeTransactions(0);
    rule.setExtraTransactionFee(BigDecimal.valueOf(5));
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(rule));

    assertThatThrownBy(() -> service.withdraw("account-1", request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Saldo insuficiente para el retiro solicitado.");
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  void compensateDepositRestoresBalanceWithoutBusinessRules() {
    Account account = activeAccount(BigDecimal.valueOf(40));
    TransactionRequest request = transaction(BigDecimal.valueOf(60));
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(accountRepository.save(any(Account.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    Account result = service.compensateDeposit("account-1", request).blockingGet();

    assertThat(result.getBalance()).isEqualByComparingTo("100");
    verify(accountRuleService, never()).getRule(any(), any(), any());
  }

  @Test
  void createDebitCardUsesRequestedCardNumberWhenAvailable() {
    Account account = activeAccount(BigDecimal.valueOf(100));
    DebitCardCreationRequest request = new DebitCardCreationRequest();
    request.setCustomerId("customer-1");
    request.setMainAccountId("account-1");
    request.setCardNumber("4555-6666-7777-8888");
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(debitCardRepository.findByCardNumber("4555-6666-7777-8888")).thenReturn(Maybe.empty());
    when(debitCardRepository.save(any(DebitCard.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    DebitCard result = service.createDebitCard(request).blockingGet();

    assertThat(result.getCardNumber()).isEqualTo("4555-6666-7777-8888");
    assertThat(result.getMainAccountId()).isEqualTo("account-1");
    assertThat(result.getCustomerId()).isEqualTo("customer-1");
  }

  @Test
  void createDebitCardRejectsDuplicateCardNumber() {
    Account account = activeAccount(BigDecimal.valueOf(100));
    DebitCardCreationRequest request = new DebitCardCreationRequest();
    request.setCustomerId("customer-1");
    request.setMainAccountId("account-1");
    request.setCardNumber("4555-6666-7777-8888");
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(debitCardRepository.findByCardNumber("4555-6666-7777-8888"))
        .thenReturn(Maybe.just(DebitCard.builder().cardNumber("4555-6666-7777-8888").build()));

    assertThatThrownBy(() -> service.createDebitCard(request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("El numero de tarjeta de debito ya existe.");
    verify(debitCardRepository, never()).save(any(DebitCard.class));
  }

  @Test
  void createAccountRejectsCustomerWithOverdueDebt() {
    AccountCreationRequest request = accountCreationRequest();
    when(customerClient.getCustomerSummary("customer-1")).thenReturn(Single.just(activeCustomer()));
    when(creditClient.hasOverdueDebt("customer-1")).thenReturn(Single.just(true));

    assertThatThrownBy(() -> service.createAccount(request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No se puede crear la cuenta. El cliente mantiene deuda vencida.");
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  void createAccountRejectsOwnershipLimitForSameActiveAccountType() {
    AccountCreationRequest request = accountCreationRequest();
    Account existing = activeAccount(BigDecimal.valueOf(10));
    AccountRuleDefinition rule = regularRule();
    rule.setMaxOwnedAccounts(1);
    when(customerClient.getCustomerSummary("customer-1")).thenReturn(Single.just(activeCustomer()));
    when(creditClient.hasOverdueDebt("customer-1")).thenReturn(Single.just(false));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(rule));
    when(accountRepository.findByCustomerId("customer-1")).thenReturn(Flowable.just(existing));

    assertThatThrownBy(() -> service.createAccount(request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "La regla ACCOUNT_RULES impide crear mas cuentas de este tipo para el cliente.");
    verify(accountRepository, never()).save(any(Account.class));
  }

  private Account activeAccount(BigDecimal balance) {
    return Account.builder()
        .id("account-1")
        .customerId("customer-1")
        .customerType("PERSONAL")
        .customerProfile("REGULAR")
        .type(AccountType.SAVINGS)
        .balance(balance)
        .transactionCount(0)
        .status(AccountStatus.ACTIVE)
        .build();
  }

  private AccountRuleDefinition regularRule() {
    AccountRuleDefinition rule = new AccountRuleDefinition();
    rule.setMaxFreeTransactions(20);
    rule.setExtraTransactionFee(BigDecimal.ZERO);
    rule.setMaintenanceFee(BigDecimal.ZERO);
    rule.setRequiresActiveCreditCard(false);
    rule.setCurrency("PEN");
    return rule;
  }

  private TransactionRequest transaction(BigDecimal amount) {
    TransactionRequest request = new TransactionRequest();
    request.setAmount(amount);
    return request;
  }

  private AccountCreationRequest accountCreationRequest() {
    AccountCreationRequest request = new AccountCreationRequest();
    request.setCustomerId("customer-1");
    request.setAccountType(AccountType.SAVINGS);
    request.setInitialBalance(BigDecimal.valueOf(100));
    return request;
  }

  private CustomerSummaryResponse activeCustomer() {
    CustomerSummaryResponse customer = new CustomerSummaryResponse();
    customer.setId("customer-1");
    customer.setType("PERSONAL");
    customer.setProfile("REGULAR");
    customer.setStatus("ACTIVE");
    return customer;
  }

  private String currentCycle() {
    LocalDate now = LocalDate.now();
    return now.getYear() + "-" + String.format("%02d", now.getMonthValue());
  }
}

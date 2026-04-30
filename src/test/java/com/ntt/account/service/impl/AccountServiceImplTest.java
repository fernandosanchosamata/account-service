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
import com.ntt.account.model.dto.CustomerProductsResponse;
import com.ntt.account.model.dto.CustomerSummaryResponse;
import com.ntt.account.model.dto.DebitCardCreationRequest;
import com.ntt.account.model.dto.LinkAccountRequest;
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
import org.mockito.ArgumentCaptor;
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
  void createAccountPersistsPersonalAccountWithDefaultProfileAndCurrency() {
    AccountCreationRequest request = accountCreationRequest();
    CustomerSummaryResponse customer = activeCustomer();
    customer.setProfile(" ");
    AccountRuleDefinition rule = regularRule();
    rule.setCurrency(" usd ");
    rule.setMaintenanceFee(BigDecimal.valueOf(7));
    rule.setMaxOwnedAccounts(null);
    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    when(customerClient.getCustomerSummary("customer-1")).thenReturn(Single.just(customer));
    when(creditClient.hasOverdueDebt("customer-1")).thenReturn(Single.just(false));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(rule));
    when(accountRepository.save(any(Account.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    Account result = service.createAccount(request).blockingGet();

    verify(accountRepository).save(accountCaptor.capture());
    assertThat(result).isSameAs(accountCaptor.getValue());
    assertThat(result.getCustomerId()).isEqualTo("customer-1");
    assertThat(result.getCustomerProfile()).isEqualTo("REGULAR");
    assertThat(result.getMaintenanceFee()).isEqualByComparingTo("7");
    assertThat(result.getCurrency()).isEqualTo("USD");
    assertThat(result.getHolders()).isEmpty();
    assertThat(result.getSigners()).isEmpty();
  }

  @Test
  void createAccountPersistsBusinessCheckingAccountWithParticipants() {
    AccountCreationRequest request = accountCreationRequest();
    request.setAccountType(AccountType.CHECKING);
    request.setHolders(java.util.List.of("holder-1"));
    request.setSigners(java.util.List.of("signer-1"));
    CustomerSummaryResponse customer = activeCustomer();
    customer.setType("EMPRESARIAL");
    AccountRuleDefinition rule = regularRule();
    rule.setMaxOwnedAccounts(2);
    when(customerClient.getCustomerSummary("customer-1")).thenReturn(Single.just(customer));
    when(creditClient.hasOverdueDebt("customer-1")).thenReturn(Single.just(false));
    when(accountRuleService.getRule(AccountType.CHECKING, "EMPRESARIAL", "REGULAR"))
        .thenReturn(Single.just(rule));
    when(accountRepository.findByCustomerId("customer-1")).thenReturn(Flowable.empty());
    when(accountRepository.save(any(Account.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    Account result = service.createAccount(request).blockingGet();

    assertThat(result.getCustomerType()).isEqualTo("EMPRESARIAL");
    assertThat(result.getType()).isEqualTo(AccountType.CHECKING);
    assertThat(result.getHolders()).containsExactly("holder-1");
    assertThat(result.getSigners()).containsExactly("signer-1");
  }

  @Test
  void createAccountRejectsInactiveCustomerBeforeCheckingDebt() {
    AccountCreationRequest request = accountCreationRequest();
    CustomerSummaryResponse customer = activeCustomer();
    customer.setStatus("INACTIVE");
    when(customerClient.getCustomerSummary("customer-1")).thenReturn(Single.just(customer));

    assertThatThrownBy(() -> service.createAccount(request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No se puede crear la cuenta para un cliente inactivo.");
    verify(creditClient, never()).hasOverdueDebt(any());
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  void createAccountRejectsBusinessSavingsAccount() {
    AccountCreationRequest request = accountCreationRequest();
    CustomerSummaryResponse customer = activeCustomer();
    customer.setType("EMPRESARIAL");
    when(customerClient.getCustomerSummary("customer-1")).thenReturn(Single.just(customer));

    assertThatThrownBy(() -> service.createAccount(request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Un cliente EMPRESARIAL no puede tener cuentas de AHORRO o PLAZO FIJO.");
    verify(creditClient, never()).hasOverdueDebt(any());
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  void createAccountRejectsPersonalAccountWithParticipants() {
    AccountCreationRequest request = accountCreationRequest();
    request.setHolders(java.util.List.of("holder-1"));
    when(customerClient.getCustomerSummary("customer-1")).thenReturn(Single.just(activeCustomer()));
    when(creditClient.hasOverdueDebt("customer-1")).thenReturn(Single.just(false));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(regularRule()));

    assertThatThrownBy(() -> service.createAccount(request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Solo las cuentas empresariales admiten titulares y firmantes.");
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  void createAccountRequiresActiveCreditCardWhenRuleDemandsIt() {
    AccountCreationRequest request = accountCreationRequest();
    AccountRuleDefinition rule = regularRule();
    rule.setRequiresActiveCreditCard(true);
    when(customerClient.getCustomerSummary("customer-1")).thenReturn(Single.just(activeCustomer()));
    when(creditClient.hasOverdueDebt("customer-1")).thenReturn(Single.just(false));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(rule));
    when(creditClient.hasActiveCreditCard("customer-1")).thenReturn(Single.just(false));

    assertThatThrownBy(() -> service.createAccount(request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("La regla ACCOUNT_RULES exige una tarjeta de credito activa para esta cuenta.");
    verify(accountRepository, never()).save(any(Account.class));
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
  void depositRefreshesRuleMetadataWhenAccountDoesNotHaveIt() {
    Account account = activeAccount(BigDecimal.valueOf(100));
    account.setCustomerType(null);
    account.setCustomerProfile(null);
    TransactionRequest request = transaction(BigDecimal.valueOf(30));
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(customerClient.getCustomerSummary("customer-1")).thenReturn(Single.just(activeCustomer()));
    when(accountRepository.save(any(Account.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(regularRule()));

    Account result = service.deposit("account-1", request).blockingGet();

    assertThat(result.getCustomerType()).isEqualTo("PERSONAL");
    assertThat(result.getCustomerProfile()).isEqualTo("REGULAR");
    assertThat(result.getBalance()).isEqualByComparingTo("130");
  }

  @Test
  void depositRejectsInactiveAccount() {
    Account account = activeAccount(BigDecimal.valueOf(100));
    account.setStatus(AccountStatus.INACTIVE);
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(regularRule()));

    assertThatThrownBy(
            () -> service.deposit("account-1", transaction(BigDecimal.TEN)).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("La cuenta no se encuentra activa.");
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  void depositRejectsWhenOutsideAllowedTransactionDay() {
    Account account = activeAccount(BigDecimal.valueOf(100));
    AccountRuleDefinition rule = regularRule();
    rule.setAllowedTransactionDay(LocalDate.now().plusDays(1).getDayOfMonth());
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(rule));

    assertThatThrownBy(
            () -> service.deposit("account-1", transaction(BigDecimal.TEN)).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Deposito denegado. Solo permitido el dia " + rule.getAllowedTransactionDay() + ".");
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  void depositRejectsSecondFixedTermMovementInSameDay() {
    Account account = activeAccount(BigDecimal.valueOf(100));
    account.setTransactionCount(1);
    account.setTransactionCycle(currentCycle());
    account.setLastTransactionDate(LocalDate.now());
    AccountRuleDefinition rule = regularRule();
    rule.setAllowedTransactionDay(LocalDate.now().getDayOfMonth());
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(rule));

    assertThatThrownBy(
            () -> service.deposit("account-1", transaction(BigDecimal.TEN)).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "La cuenta a plazo fijo solo permite un movimiento en el dia configurado del mes.");
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  void depositRejectsWhenAmountDoesNotCoverExtraTransactionFee() {
    Account account = activeAccount(BigDecimal.ZERO);
    AccountRuleDefinition rule = regularRule();
    rule.setMaxFreeTransactions(0);
    rule.setExtraTransactionFee(BigDecimal.valueOf(5));
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(rule));

    assertThatThrownBy(
            () -> service.deposit("account-1", transaction(BigDecimal.ONE)).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("El deposito no cubre la comision configurada para esta cuenta.");
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  void withdrawSubtractsAmountAndResetsMonthlyCycle() {
    Account account = activeAccount(BigDecimal.valueOf(100));
    account.setTransactionCycle("2020-01");
    account.setTransactionCount(12);
    account.setLastTransactionDate(LocalDate.of(2020, 1, 10));
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));
    when(accountRuleService.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR"))
        .thenReturn(Single.just(regularRule()));
    when(accountRepository.save(any(Account.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    Account result =
        service.withdraw("account-1", transaction(BigDecimal.valueOf(25))).blockingGet();

    assertThat(result.getBalance()).isEqualByComparingTo("75");
    assertThat(result.getTransactionCount()).isEqualTo(1);
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
  void getProductsByCustomerIdReturnsAccountsAndDebitCards() {
    Account account = activeAccount(BigDecimal.valueOf(100));
    DebitCard card = DebitCard.builder().id("card-1").customerId("customer-1").build();
    when(customerClient.getCustomerSummary("customer-1")).thenReturn(Single.just(activeCustomer()));
    when(accountRepository.findByCustomerId("customer-1")).thenReturn(Flowable.just(account));
    when(debitCardRepository.findByCustomerId("customer-1")).thenReturn(Flowable.just(card));

    CustomerProductsResponse result = service.getProductsByCustomerId("customer-1").blockingGet();

    assertThat(result.getCustomerId()).isEqualTo("customer-1");
    assertThat(result.getAccounts()).containsExactly(account);
    assertThat(result.getDebitCards()).containsExactly(card);
  }

  @Test
  void getAccountBalanceRejectsMissingAccount() {
    when(accountRepository.findById("missing")).thenReturn(Maybe.empty());

    assertThatThrownBy(() -> service.getAccountBalance("missing").blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cuenta no encontrada.");
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
  void createDebitCardRejectsWhenMainAccountBelongsToAnotherCustomer() {
    Account account = activeAccount(BigDecimal.valueOf(100));
    DebitCardCreationRequest request = new DebitCardCreationRequest();
    request.setCustomerId("customer-2");
    request.setMainAccountId("account-1");
    when(accountRepository.findById("account-1")).thenReturn(Maybe.just(account));

    assertThatThrownBy(() -> service.createDebitCard(request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("La cuenta principal no pertenece al cliente indicado.");
    verify(debitCardRepository, never()).save(any(DebitCard.class));
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
  void linkAccountAddsSecondaryAccount() {
    DebitCard card =
        DebitCard.builder()
            .id("card-1")
            .customerId("customer-1")
            .mainAccountId("main-account")
            .build();
    Account secondary = activeAccount(BigDecimal.valueOf(80));
    secondary.setId("secondary-account");
    LinkAccountRequest request = new LinkAccountRequest();
    request.setSecondaryAccountId("secondary-account");
    when(debitCardRepository.findById("card-1")).thenReturn(Maybe.just(card));
    when(accountRepository.findById("secondary-account")).thenReturn(Maybe.just(secondary));
    when(debitCardRepository.save(any(DebitCard.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    DebitCard result = service.linkAccount("card-1", request).blockingGet();

    assertThat(result.getSecondaryAccountIds()).containsExactly("secondary-account");
  }

  @Test
  void linkAccountRejectsDifferentCustomerAccount() {
    DebitCard card =
        DebitCard.builder()
            .id("card-1")
            .customerId("customer-1")
            .mainAccountId("main-account")
            .build();
    Account secondary = activeAccount(BigDecimal.valueOf(80));
    secondary.setId("secondary-account");
    secondary.setCustomerId("customer-2");
    LinkAccountRequest request = new LinkAccountRequest();
    request.setSecondaryAccountId("secondary-account");
    when(debitCardRepository.findById("card-1")).thenReturn(Maybe.just(card));
    when(accountRepository.findById("secondary-account")).thenReturn(Maybe.just(secondary));

    assertThatThrownBy(() -> service.linkAccount("card-1", request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Solo se pueden vincular cuentas del mismo cliente.");
    verify(debitCardRepository, never()).save(any(DebitCard.class));
  }

  @Test
  void linkAccountRejectsAlreadyLinkedAccount() {
    DebitCard card =
        DebitCard.builder()
            .id("card-1")
            .customerId("customer-1")
            .mainAccountId("main-account")
            .secondaryAccountIds(java.util.List.of("secondary-account"))
            .build();
    Account secondary = activeAccount(BigDecimal.valueOf(80));
    secondary.setId("secondary-account");
    LinkAccountRequest request = new LinkAccountRequest();
    request.setSecondaryAccountId("secondary-account");
    when(debitCardRepository.findById("card-1")).thenReturn(Maybe.just(card));
    when(accountRepository.findById("secondary-account")).thenReturn(Maybe.just(secondary));

    assertThatThrownBy(() -> service.linkAccount("card-1", request).blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("La cuenta ya se encuentra vinculada a esta tarjeta.");
    verify(debitCardRepository, never()).save(any(DebitCard.class));
  }

  @Test
  void getDebitCardByNumberReturnsExistingCard() {
    DebitCard card = DebitCard.builder().cardNumber("4555").build();
    when(debitCardRepository.findByCardNumber("4555")).thenReturn(Maybe.just(card));

    DebitCard result = service.getDebitCardByNumber("4555").blockingGet();

    assertThat(result).isSameAs(card);
  }

  @Test
  void getDebitCardByNumberRejectsMissingCard() {
    when(debitCardRepository.findByCardNumber("missing")).thenReturn(Maybe.empty());

    assertThatThrownBy(() -> service.getDebitCardByNumber("missing").blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Tarjeta de debito no encontrada.");
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

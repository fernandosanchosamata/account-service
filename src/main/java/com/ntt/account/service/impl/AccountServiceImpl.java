package com.ntt.account.service.impl;

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
import com.ntt.account.repository.AccountRepository;
import com.ntt.account.repository.DebitCardRepository;
import com.ntt.account.service.AccountRuleService;
import com.ntt.account.service.AccountService;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

  private static final String ACTIVE_STATUS = "ACTIVE";
  private static final String DEFAULT_PROFILE = "REGULAR";

  private final AccountRepository accountRepository;
  private final DebitCardRepository debitCardRepository;
  private final CreditClient creditClient;
  private final CustomerClient customerClient;
  private final AccountRuleService accountRuleService;

  @Override
  public Single<Account> createAccount(AccountCreationRequest request) {
    return customerClient
        .getCustomerSummary(request.getCustomerId())
        .flatMap(
            customer -> {
              validateActiveCustomer(customer);
              validateBusinessRulesForAccountCreation(request, customer);

              return creditClient
                  .hasOverdueDebt(request.getCustomerId())
                  .flatMap(
                      hasDebt -> {
                        if (Boolean.TRUE.equals(hasDebt)) {
                          return Single.error(
                              new IllegalArgumentException(
                                  "No se puede crear la cuenta. El cliente mantiene deuda vencida."));
                        }

                        return accountRuleService
                            .getRule(
                                request.getAccountType(),
                                customer.getType(),
                                resolveProfile(customer.getProfile()))
                            .flatMap(
                                rule ->
                                    validateCreditCardRequirement(customer, rule)
                                        .flatMap(
                                            ignored ->
                                                validateOwnershipLimit(request, rule)
                                                    .flatMap(
                                                        next ->
                                                            persistAccount(
                                                                request, customer, rule))));
                      });
            });
  }

  @Override
  public Single<CustomerProductsResponse> getProductsByCustomerId(String customerId) {
    return customerClient
        .getCustomerSummary(customerId)
        .flatMap(
            customer ->
                Single.zip(
                    accountRepository.findByCustomerId(customerId).toList(),
                    debitCardRepository.findByCustomerId(customerId).toList(),
                    (accounts, debitCards) ->
                        CustomerProductsResponse.builder()
                            .customerId(customerId)
                            .accounts(accounts)
                            .debitCards(debitCards)
                            .build()));
  }

  @Override
  public Single<Account> getAccountBalance(String accountId) {
    return accountRepository
        .findById(accountId)
        .switchIfEmpty(Single.error(new IllegalArgumentException("Cuenta no encontrada.")));
  }

  @Override
  public Single<Account> deposit(String accountId, TransactionRequest request) {
    return accountRepository
        .findById(accountId)
        .switchIfEmpty(Single.error(new IllegalArgumentException("Cuenta no encontrada.")))
        .flatMap(
            account ->
                resolveRuleForAccount(account)
                    .flatMap(
                        rule -> {
                          validateActiveAccount(account);
                          resetMonthlyTransactionCycleIfNeeded(account);
                          validateAllowedTransactionDay(rule, "Deposito");
                          validateFixedTermSingleMovement(rule, account);

                          int nextTransactionCount = account.getTransactionCount() + 1;
                          BigDecimal fee = calculateExtraTransactionFee(rule, nextTransactionCount);
                          BigDecimal resultingBalance =
                              account.getBalance().add(request.getAmount()).subtract(fee);

                          if (resultingBalance.compareTo(BigDecimal.ZERO) < 0) {
                            return Single.error(
                                new IllegalArgumentException(
                                    "El deposito no cubre la comision configurada para esta cuenta."));
                          }

                          account.setBalance(resultingBalance);
                          account.setTransactionCount(nextTransactionCount);
                          account.setTransactionCycle(currentTransactionCycle());
                          account.setLastTransactionDate(LocalDate.now());

                          return accountRepository.save(account);
                        }));
  }

  @Override
  public Single<Account> withdraw(String accountId, TransactionRequest request) {
    return accountRepository
        .findById(accountId)
        .switchIfEmpty(Single.error(new IllegalArgumentException("Cuenta no encontrada.")))
        .flatMap(
            account ->
                resolveRuleForAccount(account)
                    .flatMap(
                        rule -> {
                          validateActiveAccount(account);
                          resetMonthlyTransactionCycleIfNeeded(account);
                          validateAllowedTransactionDay(rule, "Retiro");
                          validateFixedTermSingleMovement(rule, account);

                          int nextTransactionCount = account.getTransactionCount() + 1;
                          BigDecimal fee = calculateExtraTransactionFee(rule, nextTransactionCount);
                          BigDecimal totalDeduction = request.getAmount().add(fee);

                          if (account.getBalance().compareTo(totalDeduction) < 0) {
                            return Single.error(
                                new IllegalArgumentException(
                                    "Saldo insuficiente para el retiro solicitado."));
                          }

                          account.setBalance(account.getBalance().subtract(totalDeduction));
                          account.setTransactionCount(nextTransactionCount);
                          account.setTransactionCycle(currentTransactionCycle());
                          account.setLastTransactionDate(LocalDate.now());

                          return accountRepository.save(account);
                        }));
  }

  @Override
  public Single<Account> compensateDeposit(String accountId, TransactionRequest request) {
    return accountRepository
        .findById(accountId)
        .switchIfEmpty(Single.error(new IllegalArgumentException("Cuenta no encontrada.")))
        .flatMap(
            account -> {
              account.setBalance(account.getBalance().add(request.getAmount()));
              return accountRepository.save(account);
            });
  }

  @Override
  public Single<DebitCard> createDebitCard(DebitCardCreationRequest request) {
    return accountRepository
        .findById(request.getMainAccountId())
        .switchIfEmpty(Single.error(new IllegalArgumentException("Cuenta principal no existe.")))
        .flatMap(
            account -> {
              if (!account.getCustomerId().equals(request.getCustomerId())) {
                return Single.error(
                    new IllegalArgumentException(
                        "La cuenta principal no pertenece al cliente indicado."));
              }

              String cardNumber = resolveDebitCardNumber(request.getCardNumber());

              DebitCard card =
                  DebitCard.builder()
                      .customerId(request.getCustomerId())
                      .mainAccountId(request.getMainAccountId())
                      .cardNumber(cardNumber)
                      .build();

              return debitCardRepository
                  .findByCardNumber(cardNumber)
                  .isEmpty()
                  .flatMap(
                      isEmpty -> {
                        if (!isEmpty) {
                          return Single.error(
                              new IllegalArgumentException(
                                  "El numero de tarjeta de debito ya existe."));
                        }
                        return debitCardRepository.save(card);
                      });
            });
  }

  @Override
  public Single<DebitCard> linkAccount(String cardId, LinkAccountRequest request) {
    return debitCardRepository
        .findById(cardId)
        .switchIfEmpty(Single.error(new IllegalArgumentException("Tarjeta de debito no existe.")))
        .flatMap(
            card ->
                accountRepository
                    .findById(request.getSecondaryAccountId())
                    .switchIfEmpty(
                        Single.error(new IllegalArgumentException("Cuenta a vincular no existe.")))
                    .flatMap(
                        account -> {
                          if (!account.getCustomerId().equals(card.getCustomerId())) {
                            return Single.error(
                                new IllegalArgumentException(
                                    "Solo se pueden vincular cuentas del mismo cliente."));
                          }

                          if (card.getMainAccountId().equals(account.getId())
                              || card.getSecondaryAccountIds().contains(account.getId())) {
                            return Single.error(
                                new IllegalArgumentException(
                                    "La cuenta ya se encuentra vinculada a esta tarjeta."));
                          }

                          if (card.getSecondaryAccountIds() == null) {
                            card.setSecondaryAccountIds(new ArrayList<>());
                          }

                          card.getSecondaryAccountIds().add(account.getId());
                          return debitCardRepository.save(card);
                        }));
  }

  @Override
  public Single<DebitCard> getDebitCardByNumber(String cardNumber) {
    return debitCardRepository
        .findByCardNumber(cardNumber)
        .switchIfEmpty(
            Single.error(new IllegalArgumentException("Tarjeta de debito no encontrada.")));
  }

  private void validateActiveCustomer(CustomerSummaryResponse customer) {
    if (!ACTIVE_STATUS.equalsIgnoreCase(customer.getStatus())) {
      throw new IllegalArgumentException("No se puede crear la cuenta para un cliente inactivo.");
    }
  }

  private Single<Boolean> validateCreditCardRequirement(
      CustomerSummaryResponse customer, AccountRuleDefinition rule) {
    if (!Boolean.TRUE.equals(rule.getRequiresActiveCreditCard())) {
      return Single.just(Boolean.TRUE);
    }

    return creditClient
        .hasActiveCreditCard(customer.getId())
        .flatMap(
            hasCard -> {
              if (!Boolean.TRUE.equals(hasCard)) {
                return Single.error(
                    new IllegalArgumentException(
                        "La regla ACCOUNT_RULES exige una tarjeta de credito activa para esta cuenta."));
              }
              return Single.just(Boolean.TRUE);
            });
  }

  private Single<Boolean> validateOwnershipLimit(
      AccountCreationRequest request, AccountRuleDefinition rule) {
    if (rule.getMaxOwnedAccounts() == null) {
      return Single.just(Boolean.TRUE);
    }

    return accountRepository
        .findByCustomerId(request.getCustomerId())
        .filter(account -> account.getType() == request.getAccountType())
        .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
        .count()
        .flatMap(
            count -> {
              if (count >= rule.getMaxOwnedAccounts()) {
                return Single.error(
                    new IllegalArgumentException(
                        "La regla ACCOUNT_RULES impide crear mas cuentas de este tipo para el cliente."));
              }
              return Single.just(Boolean.TRUE);
            });
  }

  private Single<Account> persistAccount(
      AccountCreationRequest request,
      CustomerSummaryResponse customer,
      AccountRuleDefinition rule) {
    validateRequestParticipants(request, customer);

    Account account =
        Account.builder()
            .customerId(request.getCustomerId())
            .customerType(customer.getType())
            .customerProfile(resolveProfile(customer.getProfile()))
            .holders(resolveHolders(request, customer))
            .signers(resolveSigners(request, customer))
            .type(request.getAccountType())
            .balance(request.getInitialBalance())
            .maintenanceFee(defaultAmount(rule.getMaintenanceFee()))
            .maxFreeTransactions(rule.getMaxFreeTransactions())
            .allowedTransactionDay(rule.getAllowedTransactionDay())
            .status(AccountStatus.ACTIVE)
            .currency(resolveCurrency(rule.getCurrency()))
            .build();

    return accountRepository.save(account);
  }

  private Single<AccountRuleDefinition> resolveRuleForAccount(Account account) {
    if (hasRuleMetadata(account)) {
      return accountRuleService.getRule(
          account.getType(), account.getCustomerType(), account.getCustomerProfile());
    }

    return customerClient
        .getCustomerSummary(account.getCustomerId())
        .flatMap(
            customer -> {
              account.setCustomerType(customer.getType());
              account.setCustomerProfile(resolveProfile(customer.getProfile()));
              return accountRepository
                  .save(account)
                  .flatMap(
                      saved ->
                          accountRuleService.getRule(
                              saved.getType(),
                              saved.getCustomerType(),
                              saved.getCustomerProfile()));
            });
  }

  private boolean hasRuleMetadata(Account account) {
    return account.getCustomerType() != null
        && !account.getCustomerType().isBlank()
        && account.getCustomerProfile() != null
        && !account.getCustomerProfile().isBlank();
  }

  private void validateActiveAccount(Account account) {
    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new IllegalArgumentException("La cuenta no se encuentra activa.");
    }
  }

  private void validateAllowedTransactionDay(AccountRuleDefinition rule, String operationName) {
    if (rule.getAllowedTransactionDay() == null) {
      return;
    }

    int currentDay = LocalDate.now().getDayOfMonth();
    if (!Integer.valueOf(currentDay).equals(rule.getAllowedTransactionDay())) {
      throw new IllegalArgumentException(
          operationName
              + " denegado. Solo permitido el dia "
              + rule.getAllowedTransactionDay()
              + ".");
    }
  }

  private void validateFixedTermSingleMovement(AccountRuleDefinition rule, Account account) {
    if (rule.getAllowedTransactionDay() == null) {
      return;
    }

    if (account.getTransactionCount() > 0
        && currentTransactionCycle().equals(account.getTransactionCycle())
        && LocalDate.now().equals(account.getLastTransactionDate())) {
      throw new IllegalArgumentException(
          "La cuenta a plazo fijo solo permite un movimiento en el dia configurado del mes.");
    }
  }

  private void resetMonthlyTransactionCycleIfNeeded(Account account) {
    String currentCycle = currentTransactionCycle();
    if (!currentCycle.equals(account.getTransactionCycle())) {
      account.setTransactionCycle(currentCycle);
      account.setTransactionCount(0);
      account.setLastTransactionDate(null);
    }
  }

  private BigDecimal calculateExtraTransactionFee(
      AccountRuleDefinition rule, int nextTransactionCount) {
    if (rule.getMaxFreeTransactions() == null || rule.getMaxFreeTransactions() < 0) {
      return BigDecimal.ZERO;
    }

    if (nextTransactionCount > rule.getMaxFreeTransactions()) {
      log.info("Aplicando comision adicional segun ACCOUNT_RULES.");
      return defaultAmount(rule.getExtraTransactionFee());
    }

    return BigDecimal.ZERO;
  }

  private String resolveProfile(String profile) {
    if (profile == null || profile.isBlank()) {
      return DEFAULT_PROFILE;
    }
    return profile.trim().toUpperCase();
  }

  private String resolveCurrency(String currency) {
    if (currency == null || currency.isBlank()) {
      return "PEN";
    }
    return currency.trim().toUpperCase();
  }

  private BigDecimal defaultAmount(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String currentTransactionCycle() {
    LocalDate now = LocalDate.now();
    return now.getYear() + "-" + String.format("%02d", now.getMonthValue());
  }

  private String resolveDebitCardNumber(String requestedCardNumber) {
    if (requestedCardNumber == null || requestedCardNumber.isBlank()) {
      return "4550-" + System.currentTimeMillis();
    }
    return requestedCardNumber.trim();
  }

  private void validateRequestParticipants(
      AccountCreationRequest request, CustomerSummaryResponse customer) {
    boolean businessCustomer = "EMPRESARIAL".equalsIgnoreCase(customer.getType());
    boolean hasHolders = request.getHolders() != null && !request.getHolders().isEmpty();
    boolean hasSigners = request.getSigners() != null && !request.getSigners().isEmpty();

    if (businessCustomer && !hasHolders) {
      throw new IllegalArgumentException(
          "Los clientes empresariales deben registrar al menos un titular para la cuenta.");
    }

    if (!businessCustomer && (hasHolders || hasSigners)) {
      throw new IllegalArgumentException(
          "Solo las cuentas empresariales admiten titulares y firmantes.");
    }
  }

  private List<String> resolveHolders(
      AccountCreationRequest request, CustomerSummaryResponse customer) {
    if (!"EMPRESARIAL".equalsIgnoreCase(customer.getType()) || request.getHolders() == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(request.getHolders());
  }

  private List<String> resolveSigners(
      AccountCreationRequest request, CustomerSummaryResponse customer) {
    if (!"EMPRESARIAL".equalsIgnoreCase(customer.getType()) || request.getSigners() == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(request.getSigners());
  }

  private void validateBusinessRulesForAccountCreation(
      AccountCreationRequest request, CustomerSummaryResponse customer) {
    boolean isEmpresarial = "EMPRESARIAL".equalsIgnoreCase(customer.getType());

    if (isEmpresarial
        && (request.getAccountType() == com.ntt.account.model.enums.AccountType.SAVINGS
            || request.getAccountType() == com.ntt.account.model.enums.AccountType.FIXED_TERM)) {
      throw new IllegalArgumentException(
          "Un cliente EMPRESARIAL no puede tener cuentas de AHORRO o PLAZO FIJO.");
    }
  }
}

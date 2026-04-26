package com.ntt.account.service;

import com.ntt.account.model.dto.AccountCreationRequest;
import com.ntt.account.model.dto.CustomerProductsResponse;
import com.ntt.account.model.dto.TransactionRequest;
import com.ntt.account.model.entity.Account;
import com.ntt.account.model.entity.DebitCard;
import io.reactivex.rxjava3.core.Single;

public interface AccountService {

  Single<Account> createAccount(AccountCreationRequest request);

  Single<CustomerProductsResponse> getProductsByCustomerId(String customerId);

  Single<Account> getAccountBalance(String accountId);

  Single<Account> deposit(String accountId, TransactionRequest request);

  Single<Account> withdraw(String accountId, TransactionRequest request);

  Single<Account> compensateDeposit(String accountId, TransactionRequest request);

  Single<DebitCard> createDebitCard(com.ntt.account.model.dto.DebitCardCreationRequest request);

  Single<DebitCard> linkAccount(
      String cardId, com.ntt.account.model.dto.LinkAccountRequest request);

  Single<DebitCard> getDebitCardByNumber(String cardNumber);
}

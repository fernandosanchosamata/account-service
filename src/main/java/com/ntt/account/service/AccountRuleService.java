package com.ntt.account.service;

import com.ntt.account.model.dto.AccountRuleDefinition;
import com.ntt.account.model.enums.AccountType;
import io.reactivex.rxjava3.core.Single;

public interface AccountRuleService {

  Single<AccountRuleDefinition> getRule(
      AccountType accountType, String customerType, String customerProfile);
}

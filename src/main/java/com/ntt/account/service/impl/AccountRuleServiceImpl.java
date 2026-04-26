package com.ntt.account.service.impl;

import com.ntt.account.config.AccountRulesProperties;
import com.ntt.account.model.dto.AccountRuleDefinition;
import com.ntt.account.model.enums.AccountType;
import com.ntt.account.service.AccountRuleService;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Service;
import reactor.adapter.rxjava.RxJava3Adapter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRuleServiceImpl implements AccountRuleService {

  private static final String REDIS_KEY_PREFIX = "ACCOUNT_RULES:";
  private static final String DEFAULT_PROFILE = "REGULAR";

  private final ReactiveRedisOperations<String, AccountRuleDefinition> accountRuleRedisOperations;
  private final AccountRulesProperties accountRulesProperties;

  @Override
  public Single<AccountRuleDefinition> getRule(
      AccountType accountType, String customerType, String customerProfile) {
    String normalizedCustomerType = normalize(customerType);
    String normalizedCustomerProfile = normalizeProfile(customerProfile);
    String definitionKey =
        buildDefinitionKey(accountType, normalizedCustomerType, normalizedCustomerProfile);
    String redisKey = REDIS_KEY_PREFIX + definitionKey;

    return RxJava3Adapter.monoToMaybe(accountRuleRedisOperations.opsForValue().get(redisKey))
        .switchIfEmpty(loadFromProperties(definitionKey, redisKey));
  }

  private Single<AccountRuleDefinition> loadFromProperties(String definitionKey, String redisKey) {
    AccountRuleDefinition definition = accountRulesProperties.getDefinitions().get(definitionKey);
    if (definition == null) {
      return Single.error(
          new IllegalArgumentException(
              "No existe configuracion ACCOUNT_RULES para la combinacion " + definitionKey + "."));
    }

    return RxJava3Adapter.monoToSingle(
            accountRuleRedisOperations
                .opsForValue()
                .set(redisKey, definition, accountRulesProperties.getCacheTtl()))
        .doOnSuccess(ignored -> log.info("ACCOUNT_RULES cargada en Redis: {}", redisKey))
        .onErrorReturn(
            throwable -> {
              log.warn(
                  "No se pudo cachear la regla {} en Redis: {}", redisKey, throwable.getMessage());
              return Boolean.FALSE;
            })
        .map(ignored -> definition);
  }

  private String buildDefinitionKey(
      AccountType accountType, String customerType, String customerProfile) {
    return accountType.name() + ":" + customerType + ":" + customerProfile;
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("No se puede resolver ACCOUNT_RULES sin customerType.");
    }
    return value.trim().toUpperCase();
  }

  private String normalizeProfile(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT_PROFILE;
    }
    return value.trim().toUpperCase();
  }
}

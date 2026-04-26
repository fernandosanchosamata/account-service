package com.ntt.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ntt.account.config.AccountRulesProperties;
import com.ntt.account.model.dto.AccountRuleDefinition;
import com.ntt.account.model.enums.AccountType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AccountRuleServiceImplTest {

  @Mock private ReactiveRedisOperations<String, AccountRuleDefinition> redisOperations;

  @Mock private ReactiveValueOperations<String, AccountRuleDefinition> valueOperations;

  private AccountRulesProperties properties;
  private AccountRuleServiceImpl service;

  @BeforeEach
  void setUp() {
    properties = new AccountRulesProperties();
    properties.setCacheTtl(Duration.ofHours(1));
    service = new AccountRuleServiceImpl(redisOperations, properties);
  }

  @Test
  void getRuleReturnsCachedDefinitionWhenRedisHasValue() {
    AccountRuleDefinition cachedRule = rule(BigDecimal.ZERO);
    when(redisOperations.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("ACCOUNT_RULES:SAVINGS:PERSONAL:REGULAR"))
        .thenReturn(Mono.just(cachedRule));

    AccountRuleDefinition result =
        service.getRule(AccountType.SAVINGS, "personal", null).blockingGet();

    assertThat(result).isSameAs(cachedRule);
  }

  @Test
  void getRuleLoadsFromPropertiesAndCachesWhenRedisMisses() {
    AccountRuleDefinition propertyRule = rule(BigDecimal.valueOf(10));
    properties.setDefinitions(Map.of("SAVINGS:PERSONAL:VIP", propertyRule));
    when(redisOperations.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("ACCOUNT_RULES:SAVINGS:PERSONAL:VIP")).thenReturn(Mono.empty());
    when(valueOperations.set(
            "ACCOUNT_RULES:SAVINGS:PERSONAL:VIP", propertyRule, Duration.ofHours(1)))
        .thenReturn(Mono.just(true));

    AccountRuleDefinition result =
        service.getRule(AccountType.SAVINGS, " personal ", " vip ").blockingGet();

    assertThat(result).isSameAs(propertyRule);
    verify(valueOperations)
        .set("ACCOUNT_RULES:SAVINGS:PERSONAL:VIP", propertyRule, Duration.ofHours(1));
  }

  @Test
  void getRuleStillReturnsPropertyDefinitionWhenRedisCacheWriteFails() {
    AccountRuleDefinition propertyRule = rule(BigDecimal.valueOf(5));
    properties.setDefinitions(Map.of("CHECKING:EMPRESARIAL:REGULAR", propertyRule));
    when(redisOperations.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("ACCOUNT_RULES:CHECKING:EMPRESARIAL:REGULAR"))
        .thenReturn(Mono.empty());
    when(valueOperations.set(
            "ACCOUNT_RULES:CHECKING:EMPRESARIAL:REGULAR", propertyRule, Duration.ofHours(1)))
        .thenReturn(Mono.error(new IllegalStateException("Redis down")));

    AccountRuleDefinition result =
        service.getRule(AccountType.CHECKING, "EMPRESARIAL", "").blockingGet();

    assertThat(result).isSameAs(propertyRule);
  }

  @Test
  void getRuleRejectsMissingCustomerType() {
    assertThatThrownBy(() -> service.getRule(AccountType.SAVINGS, " ", "REGULAR").blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No se puede resolver ACCOUNT_RULES sin customerType.");
  }

  @Test
  void getRuleRejectsMissingConfiguration() {
    when(redisOperations.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("ACCOUNT_RULES:SAVINGS:PERSONAL:REGULAR")).thenReturn(Mono.empty());

    assertThatThrownBy(
            () -> service.getRule(AccountType.SAVINGS, "PERSONAL", "REGULAR").blockingGet())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "No existe configuracion ACCOUNT_RULES para la combinacion SAVINGS:PERSONAL:REGULAR.");
  }

  private AccountRuleDefinition rule(BigDecimal maintenanceFee) {
    AccountRuleDefinition rule = new AccountRuleDefinition();
    rule.setMaintenanceFee(maintenanceFee);
    rule.setMaxFreeTransactions(20);
    rule.setCurrency("PEN");
    return rule;
  }
}

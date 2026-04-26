package com.ntt.account.config;

import com.ntt.account.model.dto.AccountRuleDefinition;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "account.rules")
public class AccountRulesProperties {
  private Duration cacheTtl = Duration.ofHours(12);
  private Map<String, AccountRuleDefinition> definitions = new HashMap<>();
}

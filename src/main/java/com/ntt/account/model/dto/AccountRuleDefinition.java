package com.ntt.account.model.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AccountRuleDefinition {
  private Integer maxOwnedAccounts;
  private Integer maxFreeTransactions;
  private BigDecimal extraTransactionFee = BigDecimal.ZERO;
  private BigDecimal maintenanceFee = BigDecimal.ZERO;
  private Integer allowedTransactionDay;
  private Boolean requiresActiveCreditCard = Boolean.FALSE;
  private BigDecimal minAverageDailyBalance = BigDecimal.ZERO;
  private String currency = "PEN";
}

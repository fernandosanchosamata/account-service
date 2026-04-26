package com.ntt.account.model.dto;

import com.ntt.account.model.entity.Account;
import com.ntt.account.model.entity.DebitCard;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerProductsResponse {
  private String customerId;
  private List<Account> accounts;
  private List<DebitCard> debitCards;
}

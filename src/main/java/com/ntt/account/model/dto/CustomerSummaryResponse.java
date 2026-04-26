package com.ntt.account.model.dto;

import lombok.Data;

@Data
public class CustomerSummaryResponse {
  private String id;
  private String type;
  private String profile;
  private String status;
  private String documentNumber;
}

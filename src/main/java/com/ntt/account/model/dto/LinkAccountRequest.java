package com.ntt.account.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LinkAccountRequest {

  @NotBlank(message = "El id de la cuenta secundaria es obligatorio")
  private String secondaryAccountId;
}

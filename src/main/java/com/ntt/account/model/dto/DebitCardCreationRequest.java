package com.ntt.account.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DebitCardCreationRequest {

  @NotBlank(message = "customerId no puede ser nulo")
  private String customerId;

  @NotBlank(message = "mainAccountId es obligatorio, toda tarjeta necesita una cuenta principal")
  private String mainAccountId;

  private String cardNumber;
}

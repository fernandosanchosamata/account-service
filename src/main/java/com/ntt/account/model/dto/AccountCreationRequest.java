package com.ntt.account.model.dto;

import com.ntt.account.model.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class AccountCreationRequest {

  @NotBlank(message = "customerId no puede ser nulo")
  private String customerId;

  @NotNull(message = "El tipo de cuenta es obligatorio")
  private AccountType accountType;

  @NotNull(message = "El saldo inicial es obligatorio")
  @DecimalMin(value = "0.0", message = "El saldo inicial debe ser >= 0")
  private BigDecimal initialBalance;

  // Solo para clientes empresariales
  private List<String> holders;
  private List<String> signers;
}

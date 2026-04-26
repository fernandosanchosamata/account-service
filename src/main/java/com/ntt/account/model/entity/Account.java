package com.ntt.account.model.entity;

import com.ntt.account.model.enums.AccountStatus;
import com.ntt.account.model.enums.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "accounts")
public class Account {

  @Id private String id;

  @Indexed private String customerId; // Mongo ObjectId of the Customer

  private String customerType;

  private String customerProfile;

  @Builder.Default private List<String> holders = new ArrayList<>();

  @Builder.Default private List<String> signers = new ArrayList<>();

  private AccountType type;

  @Builder.Default private BigDecimal balance = BigDecimal.ZERO;

  @Builder.Default private BigDecimal maintenanceFee = BigDecimal.ZERO;

  @Builder.Default private Integer maxFreeTransactions = 20;

  @Builder.Default private Integer transactionCount = 0;

  private String transactionCycle;

  private LocalDate lastTransactionDate;

  private Integer allowedTransactionDay; // e.g. 15 for Fixed Term

  @Builder.Default private BigDecimal averageDailyBalance = BigDecimal.ZERO; // For VIP

  @Builder.Default private AccountStatus status = AccountStatus.ACTIVE;

  @Builder.Default private String currency = "PEN";
}

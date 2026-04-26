package com.ntt.account.model.entity;

import com.ntt.account.model.enums.DebitCardStatus;
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
@Document(collection = "debit_cards")
public class DebitCard {

  @Id private String id;

  @Indexed(unique = true)
  private String cardNumber;

  private String mainAccountId; // Required reference to primary Account

  @Builder.Default private List<String> secondaryAccountIds = new ArrayList<>();

  @Indexed private String customerId;

  @Builder.Default private DebitCardStatus status = DebitCardStatus.ACTIVE;
}

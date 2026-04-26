package com.ntt.account.repository;

import com.ntt.account.model.entity.Account;
import com.ntt.account.model.entity.DebitCard;
import com.ntt.account.model.enums.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
@Testcontainers(disabledWithoutDocker = true)
class AccountRepositoryIT {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DebitCardRepository debitCardRepository;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Test
    void findByCustomerIdReturnsOnlyAccountsForRequestedCustomer() {
        Account first = account("customer-1", AccountType.SAVINGS, BigDecimal.valueOf(100));
        Account second = account("customer-1", AccountType.CHECKING, BigDecimal.valueOf(200));
        Account otherCustomer = account("customer-2", AccountType.SAVINGS, BigDecimal.valueOf(300));
        accountRepository.save(first).blockingGet();
        accountRepository.save(second).blockingGet();
        accountRepository.save(otherCustomer).blockingGet();

        List<Account> result = accountRepository.findByCustomerId("customer-1").toList().blockingGet();

        assertThat(result)
                .hasSize(2)
                .extracting(Account::getCustomerId)
                .containsOnly("customer-1");
    }

    @Test
    void findByCardNumberReturnsPersistedDebitCard() {
        DebitCard card = DebitCard.builder()
                .customerId("customer-1")
                .mainAccountId("account-1")
                .cardNumber("4555-6666-7777-8888")
                .build();
        debitCardRepository.save(card).blockingGet();

        DebitCard result = debitCardRepository.findByCardNumber("4555-6666-7777-8888").blockingGet();

        assertThat(result.getCustomerId()).isEqualTo("customer-1");
        assertThat(result.getMainAccountId()).isEqualTo("account-1");
        assertThat(result.getCardNumber()).isEqualTo("4555-6666-7777-8888");
    }

    private Account account(String customerId, AccountType type, BigDecimal balance) {
        return Account.builder()
                .customerId(customerId)
                .customerType("PERSONAL")
                .customerProfile("REGULAR")
                .type(type)
                .balance(balance)
                .build();
    }
}

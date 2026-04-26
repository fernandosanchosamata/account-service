package com.ntt.account.repository;

import com.ntt.account.model.entity.Account;
import io.reactivex.rxjava3.core.Flowable;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends RxJava3CrudRepository<Account, String> {
  Flowable<Account> findByCustomerId(String customerId);
}

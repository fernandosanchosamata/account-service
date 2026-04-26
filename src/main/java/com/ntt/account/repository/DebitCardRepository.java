package com.ntt.account.repository;

import com.ntt.account.model.entity.DebitCard;
import io.reactivex.rxjava3.core.Flowable;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DebitCardRepository extends RxJava3CrudRepository<DebitCard, String> {

  Flowable<DebitCard> findByCustomerId(String customerId);

  io.reactivex.rxjava3.core.Maybe<DebitCard> findByCardNumber(String cardNumber);
}

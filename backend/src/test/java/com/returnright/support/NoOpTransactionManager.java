package com.returnright.support;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs {@link TransactionTemplate} callbacks directly, for unit tests without a database. */
public class NoOpTransactionManager implements PlatformTransactionManager {

  public static TransactionTemplate template() {
    return new TransactionTemplate(new NoOpTransactionManager());
  }

  @Override
  public TransactionStatus getTransaction(TransactionDefinition definition) {
    return new SimpleTransactionStatus();
  }

  @Override
  public void commit(TransactionStatus status) {}

  @Override
  public void rollback(TransactionStatus status) {}
}

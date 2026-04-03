package com.solfini.matchengine.executionexchange.xchangewrappers;

import org.knowm.xchange.service.account.AccountService;

public class XAccountService implements AccountService {
  private final AccountService accountService;

  public XAccountService(final AccountService accountService) {
    this.accountService = accountService;
  }
}

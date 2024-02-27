package com.solfini.matchengine.copytrade.xchangewrappers;

import org.knowm.xchange.service.account.AccountService;

public class XAccountService implements AccountService {
  private final AccountService accountService;

  public XAccountService(AccountService accountService) {
    this.accountService = accountService;
  }
}

package com.solfini.matchengine.executionexchange.xchangewrappers;

import org.knowm.xchange.service.account.AccountService;

public class XBinanceAccountService extends XAccountService {
  public XBinanceAccountService(final AccountService accountService) {
    super(accountService);
  }
}

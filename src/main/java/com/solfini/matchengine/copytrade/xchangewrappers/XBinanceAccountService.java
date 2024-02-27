package com.solfini.matchengine.copytrade.xchangewrappers;

import org.knowm.xchange.service.account.AccountService;

public class XBinanceAccountService extends XAccountService {
  public XBinanceAccountService(AccountService accountService) {
    super(accountService);
  }
}

package com.solfini.report.check;

import com.google.common.collect.Lists;
import com.solfini.matchengine.orderbook.validator.OrderBookValidator;
import com.solfini.matchengine.orderbook.validator.OrderBookValidatorFactory;

import java.util.List;
import java.util.Set;

public class StatesChecker {

    public StatesCheckReport checkStatus() {
        StatesCheckReport checkReport = new StatesCheckReport();
        List<List<String>> orderBookCheckResult = validateOrderBook();
        checkReport.setOrderBookCheckResult(orderBookCheckResult);
        return checkReport;
    }

    protected List<List<String>> validateOrderBook() {

        List<List<String>> allResult = Lists.newArrayList();
        Set<OrderBookValidator> validators = OrderBookValidatorFactory.getOrderBookValidators();

        for (OrderBookValidator validator : validators) {
            allResult.add(validator.validate());
        }

        return allResult;
    }


}

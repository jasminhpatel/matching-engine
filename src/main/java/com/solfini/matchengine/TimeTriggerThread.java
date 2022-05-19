package com.solfini.matchengine;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.concurrent.IdleStrategy;

import com.solfini.common.AdminMessage;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.AssetFundingRate;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.admin.FundingRateCalcMessage;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.pool.CancelOrderMatchThreadObjectPool;
import com.solfini.util.FastArrayList;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class TimeTriggerThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(TimeTriggerThread.class);

  private final ManyToOneConcurrentArrayQueueCustom<Message> riskToMatcherQueue;
  private final IdleStrategy idleStrategy;
  private final CopyOnWriteArrayList<AdminMessage> list = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<Order> orderList = new CopyOnWriteArrayList<>();

  private FundingRateCalcMessage lastFundingRateCalcMessage = null;

  public TimeTriggerThread(final IdleStrategy idleStrategy) {
    this.riskToMatcherQueue = Context.getRiskToMatcherQueue();
    this.idleStrategy = idleStrategy;
  }

  public final boolean registerMessage(final AdminMessage adminMessage) {
    try {
      if (adminMessage.getTriggerTimeMillis() <= System.currentTimeMillis() + 5_000) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_6, "registerMessage SKIPPING TimeTriggerThread adminMessage=", adminMessage, CURRENTTIME_EQ,
              System.currentTimeMillis(), TRIGGERTIME_EQ, adminMessage.getTriggerTimeMillis());
        }
        return false;
      }

      // check for duplicates
      if (adminMessage instanceof FundingRateCalcMessage) {
        if (adminMessage instanceof FundingRateCalcMessage && adminMessage.equals(lastFundingRateCalcMessage)) {
          if (LOGGER.isInfoEnabled()) {
            LOGGER.info(LOG_FMT_6, "registerMessage SKIPPING duplicate adminMessage=", adminMessage, CURRENTTIME_EQ,
                System.currentTimeMillis(), TRIGGERTIME_EQ, adminMessage.getTriggerTimeMillis());
          }
          return false;
        }
        lastFundingRateCalcMessage = (FundingRateCalcMessage) adminMessage;

        // set ExternalFundingRate
        final List<AssetFundingRate> assetFundingList = lastFundingRateCalcMessage.getAssetFundingList();
        if (assetFundingList != null) {
          for (final AssetFundingRate assetFundingRate : assetFundingList) {
            if (assetFundingRate == null)
              continue;
            final InstrumentPair pair = InstrumentCache.getPair(assetFundingRate.getAssetId());

            pair.setExternalFundingRate(StringUtil.toDouble(assetFundingRate.getRate()));
            pair.setEstFundingRate(pair.getExternalFundingRate());
            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_6, "registerMessage assetFundingRate.getAssetId()=", assetFundingRate.getAssetId(),
                  ", externalFundingRate=", pair.getExternalFundingRate(), CURRENTTIME_EQ, System.currentTimeMillis(), TRIGGERTIME_EQ,
                  adminMessage.getTriggerTimeMillis());
            }
          }
        }
        return true;
      }

      list.add(adminMessage);
      list.sort(timeComparator);
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_6, "registerMessage TimeTriggerThread adminMessage=", adminMessage, CURRENTTIME_EQ, System.currentTimeMillis(),
            TRIGGERTIME_EQ, adminMessage.getTriggerTimeMillis());
      }
      return true;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return false;
  }

  // called from matching thread, addOrder
  public final boolean registerMessage(final Order algoOrder) {
    try {
      orderList.add(algoOrder);
      orderList.sort(orderTimeComparator);
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_6, "registerMessage TimeTriggerThread algoOrder=", algoOrder, CURRENTTIME_EQ, System.currentTimeMillis(),
            TRIGGERTIME_EQ, algoOrder.getExpireTime());
      }
      return true;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return false;
  }

  // called from matching thread, lookupOrder for cancelOrder
  public final Order removeOrder(final CancelOrder cancelOrder) {
    try {
      for (int i = 0; i < orderList.size(); i++) {
        final Order algoOrder = orderList.get(i);
        if (algoOrder.getOrderId() == cancelOrder.getOrigOrderId()) {
          orderList.remove(i);
          return algoOrder;
        }
        if (algoOrder.getClOrdId() != null && algoOrder.getClOrdId().equals(cancelOrder.getClOrdId())) {
          orderList.remove(i);
          return algoOrder;
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }

  public void run() {
    final FastArrayList<AdminMessage> removeList = new FastArrayList<>(1024);
    final FastArrayList<Order> removeOrderList = new FastArrayList<>(1024);

    while (true) {
      try {
        for (int i = 0; i < list.size(); i++) {
          final AdminMessage adminMessage = list.get(i);
          if (adminMessage != null && System.currentTimeMillis() >= adminMessage.getTriggerTimeMillis()) {
            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_4, "TimeTriggerThread, currentTime=", System.currentTimeMillis(), ADMINMESSAGE_EQ, adminMessage);
            }
            removeList.add(adminMessage);
            riskToMatcherQueue.addGuaranteed(adminMessage);
          }
        }

        list.removeAll(removeList);
        removeList.clear();

        for (int i = 0; i < orderList.size(); i++) {
          final Order algoOrder = orderList.get(i);
          if (algoOrder != null && System.currentTimeMillis() >= algoOrder.getExpireTime()) {
            if (LOGGER.isInfoEnabled()) {
              LOGGER.info(LOG_FMT_4, "TimeTriggerThread, currentTime=", System.currentTimeMillis(), ADMINMESSAGE_EQ, algoOrder);
            }

            // process algoOrder
            final InstrumentPair pair = InstrumentCache.getPair(algoOrder.getSecurityId());
            if (pair == null)
              continue;
            final OrderBook orderBook = pair.getOrderBook();
            if (orderBook == null)
              continue;

            // build sliced order from algoOrder source
            final Order order = orderBook.buildAlgoOrder(algoOrder);
            riskToMatcherQueue.addGuaranteed(order);

            algoOrder.incrementTriggerTimeMillis();
            if (algoOrder.intervalCountDecrement() <= 0) {
              removeOrderList.add(algoOrder);

              final CancelOrder cancelOrder = CancelOrderMatchThreadObjectPool.get();
              cancelOrder.set(algoOrder, algoOrder.getOrderId(), 1);
              cancelOrder.setUser(algoOrder.getUser());
              cancelOrder.setType(algoOrder.getType());
              riskToMatcherQueue.addGuaranteed(cancelOrder);
            }
          }
        }

        orderList.removeAll(removeOrderList);
        removeOrderList.clear();

        idleStrategy.idle();
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }

  // sort by smallest to largest
  private static final Comparator<AdminMessage> timeComparator = new Comparator<AdminMessage>() {
    @Override
    public int compare(final AdminMessage msg1, final AdminMessage msg2) {
      if ((msg1 == null && msg2 == null) || (msg1 != null && msg2 != null && msg1.getTriggerTimeMillis() == msg2.getTriggerTimeMillis()))
        return 0;
      else if (msg1 == null || (msg2 != null && msg1.getTriggerTimeMillis() > msg2.getTriggerTimeMillis()))
        return 1;
      else
        return -1;
    }
  };

  // sort by smallest to largest
  private static final Comparator<Order> orderTimeComparator = new Comparator<Order>() {
    @Override
    public int compare(final Order msg1, final Order msg2) {
      if ((msg1 == null && msg2 == null) || (msg1 != null && msg2 != null && msg1.getExpireTime() == msg2.getExpireTime()))
        return 0;
      else if (msg1 == null || (msg2 != null && msg1.getExpireTime() > msg2.getExpireTime()))
        return 1;
      else
        return -1;
    }
  };
}

package com.solfini.report;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.ReusableLog;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.matchengine.LoggingThread;
import com.solfini.matchengine.PublisherEncoderThread;
import com.solfini.matchengine.TimeEventGeneratorThread;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.pool.BalanceAdminMessageObjectPool;
import com.solfini.pool.BusinessRejectObjectPool;
import com.solfini.pool.ByteBufferObjectPool;
import com.solfini.pool.CancelOrderMatchThreadObjectPool;
import com.solfini.pool.CancelOrderObjectPool;
import com.solfini.pool.CancelRejectObjectPool;
import com.solfini.pool.DRCancelOrderObjectPool;
import com.solfini.pool.DRExecutionReportObjectPool;
import com.solfini.pool.DROrderObjectPool;
import com.solfini.pool.DRRecieverDataObjectPool;
import com.solfini.pool.ExecutionReportObjectPool;
import com.solfini.pool.KafkaProducerRecordObjectPool;
import com.solfini.pool.LiquidationOrderObjectPool;
import com.solfini.pool.OrderMatchingThreadObjectPool;
import com.solfini.pool.OrderObjectPool;
import com.solfini.pool.PositionMatchThreadObjectPool;
import com.solfini.pool.PositionReportObjectPool;
import com.solfini.pool.ReusableLogPool;
import com.solfini.pool.StringBuilderObjectPool;
import com.solfini.pool.UserOpenOrdersByPairMatchThreadObjectPool;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.risk.InsuranceState;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.MailUtil;
import com.solfini.util.ProcessMonitor;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class ReportUtil implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(ReportUtil.class);

  public static final String TR_TD = "<tr><td>";
  public static final String END_TD_TR = "</td></tr>";
  public static final String TABLE = "<table>";
  public static final String END_TABLE = "</table>";
  public static final String TD = "<td>";
  public static final String END_TD = "</td>";
  public static final String END_TR = "</tr>";
  private static final ManyToOneConcurrentArrayQueueCustom<ReusableLog> loggingQueue = LoggingThread.getLoggingQueue();
  private static long kafkaOffset;
  private static long sendTime;

  private ReportUtil() {
    // do nothing
  }

  public static final void onFirstMessage(final long kafkaOffset_, final long sendTime_, final String readData_) {
    kafkaOffset = kafkaOffset_;
    sendTime = sendTime_;
  }

  public static final void generate() {
    StringBuilder sb = new StringBuilder();
    try {
      final Runtime runtime = Runtime.getRuntime();
      final int processId = ProcessMonitor.getStartProcessId();
      final List<String> histo = Context.isEnableHistoReport() ? ProcessMonitor.getHisto(processId) : new ArrayList<>();
      final int threadCount = Thread.activeCount();
      final Thread[] th = new Thread[threadCount];
      Thread.enumerate(th);

      sb.append("<h1>Summary</h1>");
      sb.append(TABLE);

      sb.append("<tr><td>Time</td><td>");
      sb.append(StringUtil.getCurrentDateYYYYMMDDHHMMSSsss());
      sb.append(END_TD_TR);

      sb.append("<tr><td>InstanceId</td><td>");
      sb.append(Context.getInstanceId());
      sb.append(END_TD_TR);

      try {
        sb.append("<tr><td>InetAddress</td><td>");
        sb.append(InetAddress.getLocalHost());
        sb.append(END_TD_TR);

      } catch (Exception e) {
      }

      sb.append("<tr><td>freeMemory</td><td>");
      sb.append(runtime.freeMemory());
      sb.append(END_TD_TR);

      sb.append("<tr><td>totalMemory</td><td>");
      sb.append(runtime.totalMemory());
      sb.append(END_TD_TR);

      sb.append("<tr><td>maxMemory</td><td>");
      sb.append(runtime.maxMemory());
      sb.append(END_TD_TR);

      sb.append("<tr><td>availableProcessors</td><td>");
      sb.append(runtime.availableProcessors());
      sb.append(END_TD_TR);

      sb.append("<tr><td>threadCount</td><td>");
      sb.append(threadCount);
      sb.append(END_TD_TR);

      sb.append("<tr><td>processId</td><td>");
      sb.append(processId);
      sb.append(END_TD_TR);

      sb.append("<tr><td>Max User</td><td>");
      sb.append(UserCache.getCapacity());
      sb.append(END_TD_TR);

      sb.append("<tr><td>First kafkaOffset</td><td>");
      sb.append(kafkaOffset);
      sb.append(END_TD_TR);

      sb.append("<tr><td>First sendTime</td><td>");
      sb.append(sendTime);
      sb.append(END_TD_TR);

      sb.append("<tr><td>isReplayFromFileEnabled</td><td>");
      sb.append(Context.isReplayFromFileEnabled());
      sb.append(END_TD_TR);

      sb.append("<tr><td>getReplayFromFileLocation</td><td>");
      sb.append(Context.getReplayFromFileLocation());
      sb.append(END_TD_TR);

      sb.append("<tr><td>isStateValidatorEnabled</td><td>");
      sb.append(Context.isStateValidatorEnabled());
      sb.append(END_TD_TR);

      sb.append("<tr><td>isCollateralSwapEnabled</td><td>");
      sb.append(Context.isCollateralSwapEnabled());
      sb.append(END_TD_TR);

      sb.append("<tr><td>isRejectDuplicateClorIdsEnabled</td><td>");
      sb.append(Context.isRejectDuplicateClorIdsEnabled());
      sb.append(END_TD_TR);

      sb.append("<tr><td>isUseOrderPoolEnabled</td><td>");
      sb.append(Context.isUseOrderPoolEnabled());
      sb.append(END_TD_TR);

      sb.append("<tr><td>isListenToIpcMarketData</td><td>");
      sb.append(Context.isListenToIpcMarketData());
      sb.append(END_TD_TR);

      sb.append("<tr><td>isListenToKafkaMarketData</td><td>");
      sb.append(Context.isListenToKafkaMarketData());
      sb.append(END_TD_TR);

      sb.append("<tr><td>isUseInsurance</td><td>");
      sb.append(InsuranceState.isUseInsurance());
      sb.append(END_TD_TR);

      sb.append("<tr><td>InsuranceAutoCloseMode</td><td>");
      sb.append(InsuranceState.getInsuranceAutoCloseMode());
      sb.append(END_TD_TR);

      sb.append("<tr><td>InsuranceLossPercentLimit</td><td>");
      sb.append(InsuranceState.getInsuranceLossPercentLimit());
      sb.append(END_TD_TR);

      sb.append("<tr><td>InsurancePositonPercentLimit</td><td>");
      sb.append(InsuranceState.getInsurancePositonPercentLimit());
      sb.append(END_TD_TR);

      sb.append("<tr><td>isLIQUIDATON_MODE</td><td>");
      sb.append(MarginPreOrderCheckAndSettle.isLIQUIDATON_MODE());
      sb.append(END_TD_TR);

      sb.append("<tr><td>FUNDING_RATE_COLLAR</td><td>");
      sb.append(TimeEventGeneratorThread.FUNDING_RATE_COLLAR);
      sb.append(END_TD_TR);

      sb.append("<tr><td>FUNDING_RATE_MIN</td><td>");
      sb.append(TimeEventGeneratorThread.FUNDING_RATE_MIN);
      sb.append(END_TD_TR);

      sb.append("<tr><td>FUNDING_RATE_INTEREST_RATE</td><td>");
      sb.append(TimeEventGeneratorThread.FUNDING_RATE_INTEREST_RATE);
      sb.append(END_TD_TR);

      sb.append(END_TABLE);

      sb.append(TABLE);
      sb.append("<tr><td>Queue</td><td>Capacity</td><td>Size</td></tr>");

      sb.append("<tr><td>ReceiverToMatcherQueue</td>");
      sb.append(TD).append(Context.getReceiverToMatcherQueue().capacity()).append(END_TD);
      sb.append(TD).append(Context.getReceiverToMatcherQueue().size()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>DRToMatcherQueue</td>");
      sb.append(TD).append(Context.getDRToMatcherQueue().capacity()).append(END_TD);
      sb.append(TD).append(Context.getDRToMatcherQueue().size()).append(END_TD);
      sb.append(END_TR);


      sb.append("<tr><td>RiskToMatcherQueue</td>");
      sb.append(TD).append(Context.getRiskToMatcherQueue().capacity()).append(END_TD);
      sb.append(TD).append(Context.getRiskToMatcherQueue().size()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>RiskToAutoLiquidatorQueue</td>");
      sb.append(TD).append(Context.getRiskToAutoLiquidatorQueue().capacity()).append(END_TD);
      sb.append(TD).append(Context.getRiskToAutoLiquidatorQueue().size()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>MarketDataBuilderQueue</td>");
      sb.append(TD).append(Context.getMarketDataBuilderQueue().capacity()).append(END_TD);
      sb.append(TD).append(Context.getMarketDataBuilderQueue().size()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>MatcherToPublisherQueue</td>");
      sb.append(TD).append(Context.getMatcherToPublisherQueue().capacity()).append(END_TD);
      sb.append(TD).append(Context.getMatcherToPublisherQueue().size()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>PublisherToKafkaPublisherQueue</td>");
      sb.append(TD).append(Context.getPublisherToKafkaPublisherQueue().capacity()).append(END_TD);
      sb.append(TD).append(Context.getPublisherToKafkaPublisherQueue().size()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>LoggingQueue</td>");
      sb.append(TD).append(loggingQueue.capacity()).append(END_TD);
      sb.append(TD).append(loggingQueue.size()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>BalanceAdminMessageObjectPool</td>");
      sb.append(TD).append(BalanceAdminMessageObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(BalanceAdminMessageObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>BusinessRejectObjectPool</td>");
      sb.append(TD).append(BusinessRejectObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(BusinessRejectObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>ByteBufferObjectPool</td>");
      sb.append(TD).append(ByteBufferObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(ByteBufferObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>CancelOrderMatchThreadObjectPool</td>");
      sb.append(TD).append(CancelOrderMatchThreadObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(CancelOrderMatchThreadObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>CancelOrderObjectPool</td>");
      sb.append(TD).append(CancelOrderObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(CancelOrderObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>CancelRejectObjectPool</td>");
      sb.append(TD).append(CancelRejectObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(CancelRejectObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      if (MarketStatus.DR_MODE == Context.getMarketStatus()) {
        sb.append("<tr><td>DRCancelOrderObjectPool</td>");
        sb.append(TD).append(DRCancelOrderObjectPool.getCapacity()).append(END_TD);
        sb.append(TD).append(DRCancelOrderObjectPool.getSize()).append(END_TD);
        sb.append(END_TR);

        sb.append("<tr><td>DRExecutionReportObjectPool</td>");
        sb.append(TD).append(DRExecutionReportObjectPool.getCapacity()).append(END_TD);
        sb.append(TD).append(DRExecutionReportObjectPool.getSize()).append(END_TD);
        sb.append(END_TR);

        sb.append("<tr><td>DROrderObjectPool</td>");
        sb.append(TD).append(DROrderObjectPool.getCapacity()).append(END_TD);
        sb.append(TD).append(DROrderObjectPool.getSize()).append(END_TD);
        sb.append(END_TR);

        sb.append("<tr><td>DRRecieverDataObjectPool</td>");
        sb.append(TD).append(DRRecieverDataObjectPool.getCapacity()).append(END_TD);
        sb.append(TD).append(DRRecieverDataObjectPool.getSize()).append(END_TD);
        sb.append(END_TR);
      }

      sb.append("<tr><td>ExecutionReportObjectPool</td>");
      sb.append(TD).append(ExecutionReportObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(ExecutionReportObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>KafkaProducerRecordObjectPool</td>");
      sb.append(TD).append(KafkaProducerRecordObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(KafkaProducerRecordObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>LiquidationOrderObjectPool</td>");
      sb.append(TD).append(LiquidationOrderObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(LiquidationOrderObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>OrderMatchingThreadObjectPool</td>");
      sb.append(TD).append(OrderMatchingThreadObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(OrderMatchingThreadObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>OrderObjectPool</td>");
      sb.append(TD).append(OrderObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(OrderObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>PositionMatchThreadObjectPool</td>");
      sb.append(TD).append(PositionMatchThreadObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(PositionMatchThreadObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>PositionObjectPool</td>");
      sb.append(END_TR);

      sb.append("<tr><td>ReusableLogPool POOL_128</td>");
      sb.append(TD).append(ReusableLogPool.getCapacity(POOL_128)).append(END_TD);
      sb.append(TD).append(ReusableLogPool.getSize(POOL_128)).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>ReusableLogPool POOL_512</td>");
      sb.append(TD).append(ReusableLogPool.getCapacity(POOL_512)).append(END_TD);
      sb.append(TD).append(ReusableLogPool.getSize(POOL_512)).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>ReusableLogPool POOL_1024</td>");
      sb.append(TD).append(ReusableLogPool.getCapacity(POOL_1024)).append(END_TD);
      sb.append(TD).append(ReusableLogPool.getSize(POOL_1024)).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>ReusableLogPool POOL_LARGE</td>");
      sb.append(TD).append(ReusableLogPool.getCapacity(POOL_LARGE)).append(END_TD);
      sb.append(TD).append(ReusableLogPool.getSize(POOL_LARGE)).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>StringBuilderObjectPool</td>");
      sb.append(TD).append(StringBuilderObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(StringBuilderObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      sb.append("<tr><td>UserOpenOrdersByPairMatchThreadObjectPool</td>");
      sb.append(TD).append(UserOpenOrdersByPairMatchThreadObjectPool.getCapacity()).append(END_TD);
      sb.append(TD).append(UserOpenOrdersByPairMatchThreadObjectPool.getSize()).append(END_TD);
      sb.append(END_TR);

      final PositionReportObjectPool[] poolArr = PositionReportObjectPool.getPoolArr();
      for (int i = 0; i < poolArr.length; i++) {
        sb.append("<tr><td>PositionReportObjectPool").append(i).append(END_TD);
        sb.append(TD).append(poolArr[i].getCapacity()).append(END_TD);
        sb.append(TD).append(poolArr[i].getSize()).append(END_TD);
        sb.append(END_TR);
      }

      final PublisherEncoderThread[] encoderThreadArr = Context.getPublisherThread().getEncoderThreadArr();
      if (encoderThreadArr != null) {
        for (int i = 0; i < encoderThreadArr.length; i++) {
          sb.append("<tr><td>encoderThreadArr").append(i).append(END_TD);
          sb.append(TD).append(encoderThreadArr[i].getCapacity()).append(END_TD);
          sb.append(TD).append(encoderThreadArr[i].getSize()).append(END_TD);
          sb.append(END_TR);
        }
      }

      sb.append(END_TABLE);



      sb.append("<h1>Threads</h1>");
      sb.append(TABLE);
      sb.append("<tr><td>Index</td><td>Id</td><td>Name</td><td>State</td><td>Alive</td><td>Daemon</td></tr>");
      for (int i = 0; i < th.length; i++) {
        final Thread thread = th[i];
        if (thread == null)
          continue;

        sb.append("<tr>");
        sb.append(TD).append(i).append(END_TD);
        sb.append(TD).append(thread.getId()).append(END_TD);
        sb.append(TD).append(thread.getName()).append(END_TD);
        sb.append(TD).append(thread.getState()).append(END_TD);
        sb.append(TD).append(thread.isAlive()).append(END_TD);
        sb.append(TD).append(thread.isDaemon()).append(END_TD);
        sb.append(END_TR);
      }
      sb.append(END_TABLE);


      if (histo != null && !histo.isEmpty()) {
        sb.append("<h1>Histo</h1>");
        sb.append(TABLE);
        for (int i = 0; i < histo.size(); i++) {
          if (i > 60 && i < histo.size() - 1)
            continue;
          sb.append(TR_TD);
          sb.append(histo.get(i));
          sb.append(END_TD_TR);
        }
        sb.append(END_TABLE);
      }

      sb.append(generatePositionsSummary());

      sb.append(generateUserSummary("EXCHANGE_USER", UserCache.getExchangeUser(), UserCache.getExchangeUserBalanceList()));
      sb.append(generateUserSummary("INSURANCE_FUND_USER", UserCache.getInsuranceFundUser(), UserCache.getInsuranceFundBalanceList()));
      sb.append(generateUserSummary("MARKET_MAKER_USER", UserCache.getMarketMakerUser(), UserCache.getMarketMakerBalanceList()));


      final String from = "info@solfini.com";
      final String[] to = {"alerts.rohanw@gmail.com"};
      final String[] cc = {"info@solfini.com"};
      final String[] bcc = new String[0];
      final String subject = "Futures Status Report";
      final String text = sb.toString();

      try {
        MailUtil.sendMail(subject, text, "text/html", from, to, cc, bcc);
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }

    } catch (Exception e) {
      LOGGER.error("error in ReportUtil", e);
    }
  }

  public static final String generateUserSummary(final String label, final User user, final List<BalanceAdminMessage> balanceAdminList) {
    if (user == null || user.getId() == 0)
      return "";

    final DecimalFormat usdDf = new DecimalFormat("$###,###,###,###,###.##");
    final StringBuilder sb = new StringBuilder();
    try {
      sb.append("<h1>" + label + "</h1>");
      sb.append(TABLE);
      sb.append("<tr><td>Id</td><td>").append(user.getId()).append(END_TD_TR);
      sb.append("<tr><td>ExternalId</td><td>").append(user.getExternalId()).append(END_TD_TR);
      sb.append("<tr><td>Type</td><td>").append(user.getUserType()).append(END_TD_TR);
      sb.append("<tr><td>FeeTier</td><td>").append(user.getFeeTierOrig()).append(END_TD_TR);
      sb.append("<tr><td>UsdValue</td><td>").append(usdDf.format(user.getUsdValue())).append(END_TD_TR);
      sb.append("<tr><td>UsdUnrealized</td><td>").append(usdDf.format(user.getUsdUnrealized())).append(END_TD_TR);
      sb.append("<tr><td>UsdMarginMaint</td><td>").append(usdDf.format(user.getUsdMarginMaintValue())).append(END_TD_TR);
      sb.append("<tr><td>UsdMarginRequired</td><td>").append(usdDf.format(user.getUsdMarginRequiredValue())).append(END_TD_TR);
      sb.append("<tr><td>UsdMargin</td><td>").append(usdDf.format(user.getUsdMarginValue())).append(END_TD_TR);
      sb.append("<tr><td>UsdNotionalPosition</td><td>").append(usdDf.format(user.getUsdNotionalPositionValue())).append(END_TD_TR);
      sb.append("<tr><td>UsdOpenOrdersRequired</td><td>").append(usdDf.format(user.getUsdOpenOrdersRequiredValue())).append(END_TD_TR);
      sb.append("<tr><td>UsdOpenOrders</td><td>").append(user.getUsdMaxExposurePositionAndOpenOrdersValue()).append(END_TD_TR);
      sb.append("<tr><td>MarginRatio</td><td>").append(user.getMarginRatio()).append(END_TD_TR);
      sb.append("<tr><td>LeverageRatio</td><td>").append(user.getLeverageRatio()).append(END_TD_TR);
      sb.append("<tr><td>OpenOrderCount</td><td>").append(user.getOpenOrderCount()).append(END_TD_TR);
      sb.append(END_TABLE);

      final Position[] arr = user.getPositionArr();
      if (arr != null) {
        sb.append(TABLE);
        sb.append("<tr><td>Pos Id</td><td>Symbol</td><td>Quantity</td><td>UsdValue</td><td>UsdMark</td></tr>");

        for (int i = 0; i < arr.length; i++) {
          final Position position = arr[i];
          if (position == null || position.getQuantity() == 0)
            continue;

          double usdMark = 0;
          String symbol = "";
          if (AssetType.ASSET == position.getAssetType()) {
            final Instrument instrument = InstrumentCache.get(position.getInstrumentId());
            if (instrument != null) {
              symbol = instrument.getSymbol();
              usdMark = instrument.getIndexFeedUsdMark();
            }
          } else {
            final InstrumentPair instrumentPair = InstrumentCache.getPair(position.getInstrumentId());
            if (instrumentPair != null) {
              symbol = instrumentPair.getSymbol();
              usdMark = instrumentPair.getIndexFeedUsdMark();
              if (usdMark == 0) {
                usdMark = instrumentPair.getOrderBook().getUsdMark();
              }
            }
          }

          sb.append("<tr>");
          sb.append(TD).append(i).append(END_TD);
          sb.append(TD).append(symbol).append(END_TD);
          sb.append(TD).append(position.getQuantity()).append(END_TD);
          sb.append(TD).append(usdDf.format(position.getUsdValue())).append(END_TD);
          sb.append(TD).append(usdDf.format(usdMark)).append(END_TD);
          sb.append(END_TR);
        }
        sb.append(END_TABLE);
      }

      if (balanceAdminList != null && !balanceAdminList.isEmpty()) {
        sb.append(TABLE);
        for (final BalanceAdminMessage balanceAdminMessage : balanceAdminList) {
          sb.append(TR_TD);
          sb.append(balanceAdminMessage.toString());
          sb.append(END_TD_TR);
        }
        sb.append(END_TABLE);
      }
    } catch (Exception e) {
      LOGGER.error("error in ReportUtil", e);
    }
    return sb.toString();
  }

  public static final String generatePositionsSummary() {
    final DecimalFormat usdDf = new DecimalFormat("$###,###,###,###,###.##");
    final StringBuilder sb = new StringBuilder();
    try {
      sb.append("<h1>Position Summary</h1>");
      sb.append(TABLE);
      sb.append(
          "<tr><td>Pos Id</td><td>Symbol</td><td>Long</td><td>Long Count</td><td>Short</td><td>Short Count</td><td>Mark</td><td>Notional</td></tr>");

      final PositionSummary[] summaryArr = UserCache.recalcPositionsSummary();
      for (int i = 0; i < summaryArr.length; i++) {
        if (summaryArr[i] == null)
          continue;

        int scaleMultiplier = 1;
        String symbol = "";
        double usdMark = 0;
        final InstrumentPair pair = InstrumentCache.getPair(summaryArr[i].getInstrumentId());
        if (pair != null) {
          scaleMultiplier = pair.getQuantityScaleMultiplier();
          symbol = pair.getSymbol();
          usdMark = pair.getUsdMark();
        } else {
          final Instrument instrument = InstrumentCache.get((summaryArr[i].getInstrumentId()));
          if (instrument != null) {
            scaleMultiplier = instrument.getQuantityMultiplier();
            symbol = instrument.getSymbol();
            usdMark = instrument.getIndexFeedUsdMark();
          }
        }
        if (scaleMultiplier == 0)
          scaleMultiplier = 1;

        double longQty = ((double) summaryArr[i].getQuantityLong()) / scaleMultiplier;
        sb.append(TR_TD).append(summaryArr[i].getInstrumentId()).append(END_TD).append(TD).append(symbol).append(END_TD).append(TD)
            .append(longQty).append(END_TD).append(TD).append(summaryArr[i].getCountLong()).append(END_TD).append(TD)
            .append(((double) summaryArr[i].getQuantityShort()) / scaleMultiplier).append(END_TD).append(TD)
            .append(summaryArr[i].getCountShort()).append(END_TD).append(TD).append(usdMark).append(END_TD).append(TD)
            .append(usdDf.format(usdMark * longQty)).append(END_TD_TR);
      }
      sb.append(END_TABLE);
    } catch (Exception e) {
      LOGGER.error("error in ReportUtil", e);
    }

    return sb.toString();
  }

  public static final String logUserSummary(final String label, final User user, final List<BalanceAdminMessage> balanceAdminList) {
    if (user == null || user.getId() == 0)
      return "";

    final DecimalFormat usdDf = new DecimalFormat("$###,###,###,###,###.##");
    final StringBuilder sb = new StringBuilder();
    try {
      LOGGER.warn(LOG_FMT_4, "user ", label, ": ", user);

      final Position[] arr = user.getPositionArr();
      if (arr != null) {
        LOGGER.warn("Pos IdSymbolQuantityUsdValueUsdMark");

        for (int i = 0; i < arr.length; i++) {
          final Position position = arr[i];
          if (position == null || position.getQuantity() == 0)
            continue;

          double usdMark = 0;
          String symbol = "";
          if (AssetType.ASSET == position.getAssetType()) {
            final Instrument instrument = InstrumentCache.get(position.getInstrumentId());
            if (instrument != null) {
              symbol = instrument.getSymbol();
              usdMark = instrument.getIndexFeedUsdMark();
            }
          } else {
            final InstrumentPair instrumentPair = InstrumentCache.getPair(position.getInstrumentId());
            if (instrumentPair != null) {
              symbol = instrumentPair.getSymbol();
              usdMark = instrumentPair.getIndexFeedUsdMark();
              if (usdMark == 0) {
                usdMark = instrumentPair.getOrderBook().getUsdMark();
              }
            }
          }

          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(String.valueOf(i));
            LOGGER.warn(symbol);
            LOGGER.warn(String.valueOf(position.getQuantity()));
            LOGGER.warn(usdDf.format(position.getUsdValue()));
            LOGGER.warn(usdDf.format(usdMark));
          }
        }
      }

      if (LOGGER.isWarnEnabled()) {
        if (balanceAdminList != null && !balanceAdminList.isEmpty()) {
          for (final BalanceAdminMessage balanceAdminMessage : balanceAdminList) {
            LOGGER.warn(balanceAdminMessage.toString());
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("error in ReportUtil", e);
    }
    return sb.toString();
  }

  public static final void logReport() {
    final Runtime runtime = Runtime.getRuntime();
    final int processId = ProcessMonitor.getStartProcessId();
    final int threadCount = Thread.activeCount();

    if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(">>> HealthMonitor Summary");
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor Time: ", StringUtil.getCurrentDateYYYYMMDDHHMMSSsss());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor InstanceId: ", Context.getInstanceId());
      try {
        LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor InetAddress: ", InetAddress.getLocalHost());
      } catch (UnknownHostException e) {
      }

      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor Mode: ", Context.getControllerMode().toString());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor freeMemory: ", runtime.freeMemory());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor totalMemory: ", runtime.totalMemory());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor maxMemory: ", runtime.maxMemory());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor availableProcessors: ", runtime.availableProcessors());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor threadCount: ", threadCount);
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor processId: ", processId);
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor Max User: ", UserCache.getCapacity());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor First kafkaOffset: ", kafkaOffset);
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor First sendTime: ", sendTime);
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor isReplayFromFileEnabled: ", Context.isReplayFromFileEnabled());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor getReplayFromFileLocation: ", Context.getReplayFromFileLocation());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor isStateValidatorEnabled: ", Context.isStateValidatorEnabled());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor isCollateralSwapEnabled: ", Context.isCollateralSwapEnabled());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor isRejectDuplicateClorIdsEnabled: ", Context.isRejectDuplicateClorIdsEnabled());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor isUseOrderPoolEnabled: ", Context.isUseOrderPoolEnabled());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor isListenToIpcMarketData: ", Context.isListenToIpcMarketData());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor isListenToKafkaMarketData: ", Context.isListenToKafkaMarketData());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor isUseInsurance: ", InsuranceState.isUseInsurance());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor InsuranceAutoCloseMode: ", InsuranceState.getInsuranceAutoCloseMode());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor InsuranceLossPercentLimit: ", InsuranceState.getInsuranceLossPercentLimit());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor InsurancePositonPercentLimit: ", InsuranceState.getInsurancePositonPercentLimit());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor isLIQUIDATON_MODE: ", MarginPreOrderCheckAndSettle.isLIQUIDATON_MODE());

      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor FUNDING_RATE_COLLAR: ", TimeEventGeneratorThread.FUNDING_RATE_COLLAR);
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor FUNDING_RATE_MIN: ", TimeEventGeneratorThread.FUNDING_RATE_MIN);
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor FUNDING_RATE_INTEREST_RATE: ", TimeEventGeneratorThread.FUNDING_RATE_INTEREST_RATE);

      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor Queue: Capacity: Size: ");

      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor ReceiverToMatcherQueue: ", (long) Context.getReceiverToMatcherQueue().capacity(), " : ",
          Context.getReceiverToMatcherQueue().size());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor DRToMatcherQueue: ", (long) Context.getDRToMatcherQueue().capacity(), " : ",
          Context.getDRToMatcherQueue().size());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor RiskToMatcherQueue: ", (long) Context.getRiskToMatcherQueue().capacity(), " : ",
          Context.getRiskToMatcherQueue().size());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor RiskToAutoLiquidatorQueue: ", (long) Context.getRiskToAutoLiquidatorQueue().capacity(),
          " : ", Context.getRiskToAutoLiquidatorQueue().size());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor MarketDataBuilderQueue: ", (long) Context.getMarketDataBuilderQueue().capacity(), " : ",
          Context.getMarketDataBuilderQueue().size());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor MatcherToPublisherQueue: ", (long) Context.getMatcherToPublisherQueue().capacity(), " : ",
          Context.getMatcherToPublisherQueue().size());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor PublisherToKafkaPublisherQueue: ",
          (long) Context.getPublisherToKafkaPublisherQueue().capacity(), " : ", Context.getPublisherToKafkaPublisherQueue().size());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor LoggingQueue: ", (long) loggingQueue.capacity(), " : ", loggingQueue.size());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor BalanceAdminMessageObjectPool: ", (long) BalanceAdminMessageObjectPool.getCapacity(), " : ",
          BalanceAdminMessageObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor BusinessRejectObjectPool: ", (long) BusinessRejectObjectPool.getCapacity(), " : ",
          BusinessRejectObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor BalanceAdminMessageObjectPool: ", (long) BalanceAdminMessageObjectPool.getCapacity(), " : ",
          BalanceAdminMessageObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor ByteBufferObjectPool: ", (long) ByteBufferObjectPool.getCapacity(), " : ",
          ByteBufferObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor CancelOrderMatchThreadObjectPool: ", (long) CancelOrderMatchThreadObjectPool.getCapacity(),
          " : ", CancelOrderMatchThreadObjectPool.getSize());
      LOGGER.warn(">>> HealthMonitor CancelOrderObjectPool: ", CancelOrderObjectPool.getCapacity(), " : ", CancelOrderObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor CancelRejectObjectPool: ", (long) CancelRejectObjectPool.getCapacity(), " : ",
          CancelRejectObjectPool.getSize());

      if (MarketStatus.DR_MODE == Context.getMarketStatus()) {
        LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor DRCancelOrderObjectPool: ", (long) DRCancelOrderObjectPool.getCapacity(), " : ",
            DRCancelOrderObjectPool.getSize());
        LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor DRExecutionReportObjectPool: ", (long) DRExecutionReportObjectPool.getCapacity(), " : ",
            DRExecutionReportObjectPool.getSize());

        LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor DROrderObjectPool: ", (long) DROrderObjectPool.getCapacity(), " : ",
            DROrderObjectPool.getSize());
        LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor DRRecieverDataObjectPool: ", (long) DRRecieverDataObjectPool.getCapacity(), " : ",
            DRRecieverDataObjectPool.getSize());
      }

      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor ExecutionReportObjectPool: ", (long) ExecutionReportObjectPool.getCapacity(), " : ",
          ExecutionReportObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor KafkaProducerRecordObjectPool: ", (long) KafkaProducerRecordObjectPool.getCapacity(), " : ",
          KafkaProducerRecordObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor LiquidationOrderObjectPool: ", (long) LiquidationOrderObjectPool.getCapacity(), " : ",
          LiquidationOrderObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor OrderMatchingThreadObjectPool: ", (long) OrderMatchingThreadObjectPool.getCapacity(), " : ",
          OrderMatchingThreadObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor OrderObjectPool: ", (long) OrderObjectPool.getCapacity(), " : ", OrderObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor PositionMatchThreadObjectPool: ", (long) PositionMatchThreadObjectPool.getCapacity(), " : ",
          PositionMatchThreadObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor ReusableLogPool POOL_128: ", (long) ReusableLogPool.getCapacity(POOL_128), " : ",
          ReusableLogPool.getSize(POOL_128));
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor ReusableLogPool POOL_512: ", (long) ReusableLogPool.getCapacity(POOL_512), " : ",
          ReusableLogPool.getSize(POOL_512));
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor ReusableLogPool POOL_1024: ", (long) ReusableLogPool.getCapacity(POOL_1024), " : ",
          ReusableLogPool.getSize(POOL_1024));
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor ReusableLogPool POOL_LARGE: ", (long) ReusableLogPool.getCapacity(POOL_LARGE), " : ",
          ReusableLogPool.getSize(POOL_LARGE));


      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor StringBuilderObjectPool: ", (long) StringBuilderObjectPool.getCapacity(), " : ",
          StringBuilderObjectPool.getSize());
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor UserOpenOrdersByPairMatchThreadObjectPool: ",
          (long) UserOpenOrdersByPairMatchThreadObjectPool.getCapacity(), " : ", UserOpenOrdersByPairMatchThreadObjectPool.getSize());

      final PositionReportObjectPool[] poolArr = PositionReportObjectPool.getPoolArr();
      for (int i = 0; i < poolArr.length; i++)
        LOGGER.warn(">>> HealthMonitor PositionReportObjectPool" + i + " : " + poolArr[i].getCapacity() + " : " + poolArr[i].getSize());

      final PublisherEncoderThread[] encoderThreadArr = Context.getPublisherThread().getEncoderThreadArr();
      if (encoderThreadArr != null) {
        for (int i = 0; i < encoderThreadArr.length; i++)
          LOGGER.warn(
              ">>> HealthMonitor encoderThreadArr" + i + " : " + encoderThreadArr[i].getCapacity() + " : " + encoderThreadArr[i].getSize());
      }


      final int count = Thread.activeCount();
      LOGGER.warn(LOG_FMT_2, ">>> HealthMonitor No. of active thread: ", count);
      final Thread[] th = new Thread[count];
      Thread.enumerate(th);
      for (int i = 0; i < count; i++) {
        LOGGER.warn(">>> HealthMonitor thread" + i + ": " + th[i] + ", state=" + th[i].getState() + ", alive=" + th[i].isAlive()
            + ", isDaemon=" + th[i].isDaemon() + ", state=" + th[i].getState() + ", id=" + th[i].getId() + NAME_EQ + th[i].getName());
      }
    }

    logUserSummary("EXCHANGE_USER", UserCache.getExchangeUser(), UserCache.getExchangeUserBalanceList());
    logUserSummary("INSURANCE_FUND_USER", UserCache.getInsuranceFundUser(), UserCache.getInsuranceFundBalanceList());
    logUserSummary("MARKET_MAKER_USER", UserCache.getMarketMakerUser(), UserCache.getMarketMakerBalanceList());

  }

  public static void main(String[] args) {
    InetAddress ip;
    String hostname;
    try {
      ip = InetAddress.getLocalHost();
      hostname = ip.getHostName();
      System.out.println("Your current IP address : " + ip);
      System.out.println("Your current Hostname : " + hostname);

    } catch (UnknownHostException e) {

      e.printStackTrace();
    }
  }
}

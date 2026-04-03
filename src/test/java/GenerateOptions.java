import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class GenerateOptions {
  private static ObjectMapper MAPPER = new ObjectMapper();
  private static final String GET_INSTRUMENTS_OPTIONS =
      "https://www.deribit.com/api/v2/public/get_instruments?currency=BTC&expired=false&kind=option";
  private static String JSON = """
      {"class":"SecurityDefinitionAdminMessage","sequenceNumber":0,"persistTime":0,"sourceSeqNum":0,"sourceSendTime":0,"snapId":0,"kafkaRecordOffset":0,"connectionId":0,"triggerTimeMillis":0,"externalId":0,"updateType":1,"routeToDestination":"ALL","securityId":%s,"symbol":"%s","name":"%s","assetType":%s,"marketStatus":0,"marketType":2,"baseId":3,"quotedId":1,"priceScale":0,"quantityScale":6,"underlyerId":52,"strikePrice":%s,"expireTimeMillis":%s,"orderBookStrategy":2,"preOrderCheckStrategy":12,"estimatedUserCount":0,"daysFeedIsActive":0,"estimatedVolatility":0.0,"estimatedVAR":0.0,"settleType":1,"maintMarginPercent":40,"requiredMarginPercent":80,"minQty":0,"maxQty":0,"maxPrice":0,"supportOrderType":0,"marginCurveId":0,"commissionType":0,"indexFeedUsdMark":42036.280000000006,"collateralMarginPercentDiscount":0,"arrSize":500000,"cacheDepth":0,"textData":"","secondaryOrderId":0,"secondaryExecId":0,"auctionStartTimeHrGMT":0,"auctionDurationTime":0,"auctionFixingAttempts":0,"auctionFixingWaitTime":0,"expireRollTimeMillis":%s,"symbolRollCount":209,"physicalSettle":false,"withdrawFee":0.0,"isWithdrawFeePercent":false,"withdrawFeeInstrument":0,"sector":10}
      """;
  private static String SQL = """
         INSERT INTO public.security_definition_log (id, sequence_number, insert_time, updatetype, securityid, symbol, "name", assettype, baseid, quotedid, pricescale, quantityscale, orderbookstrategy, preordercheckstrategy, settletype, maintmarginpercent, requiredmarginpercent, usdmark, status, estimatedusercount, estimatedvolatility, daysfeedisactive, estimatedvar, sortorder, tenure, symbolrollcount, expiretimemillis, expirerolltimemillis, minpriceincrement, minpriceincrementamount, minimumfillsize, qtytype, contractmultiplier, issuedate, strikecurrencyid, description, cficode, miccode, markettype) VALUES(%s, %s, '20250103-00:00:00.000', 1, %s, '%s', '%s', %s, 3, 1, 0, 6, 2, 12, 1, 40, 80, %s, 1, 0, 0.0, 0, 0.0, 0, '%s', 208, %s, %s, 0.01, 0.01, 0, 0, 0.01, 0, 1, NULL, NULL, NULL, 2) ON CONFLICT (id) DO UPDATE SET symbol = EXCLUDED.symbol, name = EXCLUDED.name, assettype = EXCLUDED.assettype, tenure = EXCLUDED.tenure,expiretimemillis = EXCLUDED.expiretimemillis, expirerolltimemillis=EXCLUDED.expirerolltimemillis;
      """;
  private static String SYMBOL = "BTC/USD[%s]%s%s_%s";
  private static String NAME = "BTC/USD[%s]%s%s_%s";
  private static int SECURITY_ID = 90;

  public static void main(String[] args) throws JsonProcessingException {
    StringBuilder[] arr = getFromDeribit();
    System.out.println(arr[0]);
    System.out.println();
    System.out.println();
    System.out.println();
    //System.out.println(arr[1]);
  }

  private static StringBuilder[] getFromDeribit() throws JsonProcessingException {
    StringBuilder jsonSb = new StringBuilder();
    StringBuilder sqlSb = new StringBuilder();
    String json = get(GET_INSTRUMENTS_OPTIONS);
    Result result = MAPPER.readValue(json, Result.class);
    for (Pair pair: result.getResult()) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
      calendar.setTimeInMillis(pair.expiry);
      if ("call".equalsIgnoreCase(pair.getType())) {
        if (SECURITY_ID == 228) {
          SECURITY_ID = 290;
          jsonSb.append("\n");
        }
        String symbol =
            String.format(SYMBOL, "C", getMonth(calendar.get(Calendar.MONTH)), calendar.get(Calendar.DAY_OF_MONTH), (int)pair.strike);
        String name = String.format(NAME, "Call", getMonth(calendar.get(Calendar.MONTH)), calendar.get(Calendar.DAY_OF_MONTH), (int)pair.strike);
        jsonSb.append(buildJson(SECURITY_ID, symbol, name, 5, (int)pair.strike, pair.expiry, (pair.expiry - pair.created)));
        sqlSb.append(buildSql(SECURITY_ID, symbol, name, 5, (int)pair.strike, pair.expiry, (pair.expiry - pair.created)));
      } else {
        String symbol = String.format(SYMBOL, "P", getMonth(calendar.get(Calendar.MONTH)), calendar.get(Calendar.DAY_OF_MONTH), (int)pair.strike);
        String name = String.format(NAME, "Put", getMonth(calendar.get(Calendar.MONTH)), calendar.get(Calendar.DAY_OF_MONTH), (int)pair.strike);
        jsonSb.append(buildJson(SECURITY_ID, symbol, name, 4, (int)pair.strike, pair.expiry, (pair.expiry - pair.created)));
        sqlSb.append(buildSql(SECURITY_ID, symbol, name, 4, (int)pair.strike, pair.expiry, (pair.expiry - pair.created)));
      }
      SECURITY_ID++;
    }
    return new StringBuilder[] {jsonSb, sqlSb};
  }

  private static String buildJson(int securityId, String symbol, String name, int assetType, int strikePrice, long expireTimeMillis, long expireRollTimeMillis) {
    return String.format(JSON, securityId, symbol, name, assetType, strikePrice, expireTimeMillis, expireRollTimeMillis);
  }

  private static String buildSql(int securityId, String symbol, String name, int assetType, int strikePrice, long expireTimeMillis, long expireRollTimeMillis) {
    return String.format(SQL, securityId, securityId, securityId, symbol, name, assetType, strikePrice, "2D", expireTimeMillis, expireRollTimeMillis);
  }

  private static String getMonth(int month) {
    switch (month) {
      case 0: return "Jan";
      case 1: return "Feb";
      case 2: return "Mar";
      case 3: return "Apr";
      case 4: return "May";
      case 5: return "Jun";
      case 6: return "Jul";
      case 7: return "Aug";
      case 8: return "Sep";
      case 9: return "Oct";
      case 10: return "Nov";
      case 11: return "Dec";
      default:return "";
    }
  }

  private static String get(final String httpsURL) {
    StringBuffer responseData = new StringBuffer();
    try {
      URL myurl = new URL(httpsURL);
      HttpsURLConnection con = (HttpsURLConnection) myurl.openConnection();
      con.setRequestMethod("GET");
      InputStream ins;
      if (con.getResponseCode() >= 400) {
        ins = con.getErrorStream();
      } else {
        ins = con.getInputStream();
      }
      BufferedReader in = new BufferedReader(new InputStreamReader(ins));
      String str;
      while ((str = in.readLine()) != null) {
        // str is one line of text; readLine() strips the newline character(s)
        responseData.append(str);
      }
      in.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
    return responseData.toString();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class Result {
    private List<Pair> result;

    public List<Pair> getResult() {
      return result;
    }

    public void setResult(List<Pair> result) {
      this.result = result;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class Pair {
    @JsonProperty("is_active")
    private boolean active;
    private double strike;
    @JsonProperty("settlement_period")
    private String period;
    @JsonProperty("creation_timestamp")
    private long created;
    @JsonProperty("expiration_timestamp")
    private long expiry;
    @JsonProperty("option_type")
    private String type;

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }

    public double getStrike() {
      return strike;
    }

    public void setStrike(double strike) {
      this.strike = strike;
    }

    public String getPeriod() {
      return period;
    }

    public void setPeriod(String period) {
      this.period = period;
    }

    public long getCreated() {
      return created;
    }

    public void setCreated(long created) {
      this.created = created;
    }

    public long getExpiry() {
      return expiry;
    }

    public void setExpiry(long expiry) {
      this.expiry = expiry;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }
  }
}


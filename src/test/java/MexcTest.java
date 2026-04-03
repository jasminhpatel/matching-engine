import static com.solfini.common.Constants.USD;
import static com.solfini.common.Constants.USDC;
import static com.solfini.common.Constants.USDT;
import static org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType.UNIFIED;
import static org.knowm.xchange.utils.DigestUtils.bytesToHex;

import com.solfini.matchengine.executionexchange.ExternalExchangeUtil;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

public class MexcTest {
  public static final String HMAC_SHA_256 = "HmacSHA256";

  public static void main(String[] args) throws Exception {

  }

}

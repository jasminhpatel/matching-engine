package com.solfini.util.snapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import com.solfini.common.Message;
import com.solfini.instrument.Balance;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;


public class SnapValidator {

  private static final int MAX_USER_TYPES = 8;
  private static final int MAX_PAIRS = 1024;

  private class PairOrder {
    private final long orderId;
    private final long secondaryOrderId;
    private final long userId;
    private final Side side;
    private final long price;
    private final long quantity;

    public PairOrder(final Order order, final int priceScale, final int quantityScale) {
      this.orderId = order.getOrderId();
      this.secondaryOrderId = order.getSecondaryOrderId();
      this.userId = order.getUser().getId();
      this.side = order.getSide();
      this.price = scale(order.getPrice(), order.getPriceScale(), priceScale);
      this.quantity = scale(order.getQty(), order.getQtyScale(), quantityScale);
    }

    @Override
    public String toString() {
      return String.format("%10s:%-10s %10s %10s %-4s %-10s", orderId, secondaryOrderId, userId, quantity, side, price);
    }

    private long scale(final long value, final int scale1, final int scale2) {
      long result = value;
      for (int i = 0; i < Math.abs(scale1 - scale2); i++) {
        if (scale1 > scale2)
          result /= 10;
        else if (scale2 > scale1)
          result *= 10;
      }
      return result;
    }
  }

  private class Pair {
    private final int priceScale;
    private final int quantityScale;
    private long balance;
    private long bestBid;
    private long bestOffer;
    private double totalUsdRealized;
    private double totalUsdUnrealized;
    private List<PairOrder> orders = new ArrayList<>();

    public Pair(final SecurityDefinitionAdminMessage message) {
      priceScale = message.getPriceScale();
      quantityScale = message.getQuantityScale();
      balance = 0;
      bestBid = -1;
      bestOffer = -1;
      totalUsdRealized = 0;
      totalUsdUnrealized = 0;
    }

    public void addOrder(final Order message) {
      PairOrder order = new PairOrder(message, priceScale, quantityScale);
      orders.add(order);
      if (order.side == Side.BUY) {
        if ((bestBid == -1) || (bestBid < order.price)) {
          bestBid = order.price;
        }
      } else if ((order.side == Side.SELL) && ((bestOffer == -1) || (bestOffer > order.price))) {
        bestOffer = order.price;
      }
    }
  }

  private final NumberFormat format = NumberFormat.getInstance();
  private final int[] userCounts = new int[MAX_USER_TYPES];
  private final Pair[] pairs = new Pair[MAX_PAIRS];

  public SnapValidator() {
    format.setGroupingUsed(true);
  }

  public void process(final Message message) {
    if (message instanceof BalanceAdminMessage) {
      process((BalanceAdminMessage) message);
    } else if (message instanceof Order) {
      process((Order) message);
    } else if (message instanceof SecurityDefinitionAdminMessage) {
      process((SecurityDefinitionAdminMessage) message);
    } else if (message instanceof UserAdminMessage) {
      process((UserAdminMessage) message);
    }
  }

  public boolean validate() {
    System.out.println("Validations:");
    boolean state = validateExchangeUser();
    state &= validateInsuranceFundUser();
    state &= validateContractBalances();
    state &= validateContractPnL();
    state &= validateCrossedBooks();

    return state;
  }

  public void process(final BalanceAdminMessage message) {
    for (Balance balance : message.getBalanceList()) {
      if (pairs[balance.getAssetId()] != null) {
        pairs[balance.getAssetId()].balance += balance.getBalance().value();
        pairs[balance.getAssetId()].totalUsdRealized += balance.getUsdRealized();
        pairs[balance.getAssetId()].totalUsdUnrealized += balance.getUsdUnrealized();
      }
    }
  }

  private void process(final Order message) {
    final Pair pair = pairs[message.getSecurityId()];
    if ((pair != null) && (message.getOrdType() == OrdType.LIMIT) && (message.getPrice() != 0) && (message.getQty() != 0)) {
      pair.addOrder(message);
    }
  }

  private void process(final SecurityDefinitionAdminMessage message) {
    if (AssetType.ASSET != message.getAssetType()) {
      pairs[message.getSecurityId()] = new Pair(message);
    }
  }

  private void process(final UserAdminMessage message) {
    userCounts[message.getUserType()] += 1;
  }

  private boolean status(final String message, final boolean state) {
    System.out.println(String.format("  %-30s: %s", message, state ? "OK" : "FAILED"));
    return state;
  }

  private void detail(final String message) {
    System.out.println("    " + message);
  }

  private boolean validateExchangeUser() {
    if (userCounts[User.EXCHANGE] != 1) {
      status("Exchange user", false);
      detail("ExchangeUserCount=" + userCounts[User.EXCHANGE]);
      return false;
    }

    return status("Exchange user", true);
  }

  private boolean validateInsuranceFundUser() {
    if (userCounts[User.INSURANCE_FUND] != 1) {
      status("Insurance fund user", false);
      detail("InsuranceFundUserCount=" + userCounts[User.INSURANCE_FUND]);
      return false;
    }

    return status("Insurance fund user", true);
  }

  private boolean validateContractBalances() {
    boolean success = true;
    for (int i = 0; i < MAX_PAIRS; i++) {
      if ((pairs[i] != null) && (pairs[i].balance != 0)) {
        if (success) {
          status("Contract balances", false);
          success = false;
        }
        detail("SecurityId=" + i + ", BalanceSum=" + format.format(pairs[i].balance));
      }
    }

    if (success) {
      status("Contract balances", true);
    }

    return success;
  }

  private boolean validateContractPnL() {
    status("Contract PnL", true);
    for (int i = 0; i < MAX_PAIRS; i++) {
      if (pairs[i] != null) {
        detail("SecurityId=" + i + ", TotalUsdRealized=" + format.format(pairs[i].totalUsdRealized) + ", TotalUsdUnrealized="
            + format.format(pairs[i].totalUsdUnrealized) + ", TotalPnLSum="
            + format.format(pairs[i].totalUsdRealized + pairs[i].totalUsdUnrealized));
      }
    }

    return true;
  }

  private boolean validateCrossedBooks() {
    boolean success = true;
    for (int i = 0; i < MAX_PAIRS; i++) {
      final Pair pair = pairs[i];
      if ((pair != null) && (pair.bestBid != -1) && (pair.bestOffer != -1) && (pair.bestBid >= pair.bestOffer)) {
        if (success) {
          status("Crossed books", false);
          success = false;
        }
        detail("SecurityId=" + i + ", BestBid=" + format.format(pair.bestBid) + ", BestOffer=" + format.format(pair.bestOffer));
        for (PairOrder order : pair.orders) {
          if ((order.side == Side.BUY) && (order.price >= pair.bestOffer)) {
            detail("  " + order);
          }
          if ((order.side == Side.SELL) && (order.price <= pair.bestBid)) {
            detail("  " + order);
          }
        }
      }
    }

    if (success) {
      status("Crossed books", true);
    }

    return success;
  }
}

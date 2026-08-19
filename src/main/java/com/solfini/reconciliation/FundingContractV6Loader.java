package com.solfini.reconciliation;

import com.solfini.util.PropertyReader;
import com.solfini.util.blockchain.util.RpcUtil;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint48;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.utils.Numeric;

/**
 * Reads live FundingContractV6 / FundingContractV6Views state — composite membership, status,
 * and pending membership proposals. Business/ops calls this generation "V2", same as everything
 * else in this package (FundManagerV2SyncJob, FUND_MANAGER_V2_CONTRACT_ADDRESS in prod config) —
 * this loader deliberately introduces NO new "_V6"-named config. The core contract address is
 * whatever FUND_MANAGER_V2_CONTRACT_ADDRESS / XDC_FUND_MANAGER_V2_CONTRACT_ADDRESS already
 * resolve to (Context.getFundManagerContractByNetworkAndVersion(network, 2)) — once ops points
 * that existing property at the new deployment, this starts working, nothing else to configure.
 * The Views companion address is never configured at all: it's read on-chain from the core
 * contract's own `viewsContract()` (see getViewsContractAddress) — set once by admin at deploy
 * time and public specifically so nothing downstream needs to know it out-of-band.
 */
public class FundingContractV6Loader {
  private static final String CALLER_ADDRESS = PropertyReader.getProperty("FUND_MANAGER_CONTRACT_ADDRESS", "");

  /** Membership-proposal fields needed to decide whether a pending couple/decouple is matured. */
  public static class MembershipProposal {
    public final String compositeId;
    public final String successorComposite;
    public final String token;
    public final long eta;
    public final int op; // 0 = NONE, 1 = COUPLE, 2 = DECOUPLE
    public final boolean executed;

    MembershipProposal(final String compositeId, final String successorComposite, final String token,
        final long eta, final int op, final boolean executed) {
      this.compositeId = compositeId;
      this.successorComposite = successorComposite;
      this.token = token;
      this.eta = eta;
      this.op = op;
      this.executed = executed;
    }

    public boolean isPending() {
      return op != 0 && !executed;
    }

    public boolean isMatured(final long nowEpochSeconds) {
      return isPending() && nowEpochSeconds >= eta;
    }
  }

  /**
   * Merge-proposal fields needed to decide whether a pending proposeMerge is matured. Separate
   * from MembershipProposal (couple/decouple) since mergeProposals is a distinct on-chain
   * registry with a different struct shape (no compositeId/token/op — a merge combines N tokens
   * from N possibly-different sources, recorded off this struct entirely; see
   * FundingContractV6.sol's mergeProposals/_mergeTokens/_mergeSource). targetComposite doubles as
   * the "does this proposal exist" sentinel, same convention the contract itself uses.
   */
  public static class MergeProposal {
    public final String targetComposite;
    public final long eta;
    public final boolean executed;

    MergeProposal(final String targetComposite, final long eta, final boolean executed) {
      this.targetComposite = targetComposite;
      this.eta = eta;
      this.executed = executed;
    }

    // Built the same way Numeric.toHexString would render an all-zero bytes32, so it's guaranteed
    // to match targetComposite's decoded format exactly (never hand-counted hex chars).
    private static final String ZERO_BYTES32 = Numeric.toHexString(new byte[32]);

    public boolean isPending() {
      return !ZERO_BYTES32.equalsIgnoreCase(targetComposite) && !executed;
    }

    public boolean isMatured(final long nowEpochSeconds) {
      return isPending() && nowEpochSeconds >= eta;
    }
  }

  // Same retry/fallback shape as PositionManagerLoader.callWithFallback — a node returning a
  // transport-level or plain JSON-RPC error shouldn't kill the whole discovery pass.
  private static final int MAX_ATTEMPTS = 5;

  private static EthCall callWithFallback(final String network, final Transaction transaction) throws IOException {
    boolean useSecondary = false, hasProxyError = false;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        final Web3j web3j = RpcUtil.createWeb3jConnection(network, null, useSecondary, hasProxyError);
        final EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();
        if (!response.hasError()) {
          return response;
        }
        System.out.println("FundingContractV6Loader RPC error. attempt: " + attempt + " reason: "
            + response.getError().getMessage() + " useSecondary: " + useSecondary + " hasProxyError: " + hasProxyError);
        useSecondary = true;
        hasProxyError = true;
      } catch (ClientConnectionException | IOException e) {
        System.out.println("FundingContractV6Loader connection error. attempt: " + attempt + " reason: "
            + e.getMessage() + " useSecondary: " + useSecondary + " hasProxyError: " + hasProxyError);
        useSecondary = true;
        hasProxyError = true;
        if (attempt == MAX_ATTEMPTS) {
          throw e;
        }
      }
    }
    throw new RuntimeException("FundingContractV6 RPC call failed after " + MAX_ATTEMPTS + " attempts");
  }

  /**
   * Reads `viewsContract()` (public, write-once, set by admin right after Views is deployed) off
   * the core contract. Call once per run per network and pass the result into the getXxx methods
   * below — no property to configure or keep in sync.
   */
  public static String getViewsContractAddress(final String network, final String coreAddress) throws IOException {
    final Function function = new Function("viewsContract", Collections.emptyList(),
        List.of(new TypeReference<Address>() {}));
    final String encoded = FunctionEncoder.encode(function);
    final Transaction tx = Transaction.createEthCallTransaction(CALLER_ADDRESS, coreAddress, encoded);
    final EthCall response = callWithFallback(network, tx);
    final List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    return ((Address) decoded.get(0)).getValue();
  }

  /** Every composite id ever created, in creation order — includes PROVISIONING ones. */
  @SuppressWarnings("unchecked")
  public static List<String> getAllComposites(final String network, final String viewsAddress) throws IOException {
    final Function function = new Function("getAllComposites", Collections.emptyList(),
        List.of(new TypeReference<DynamicArray<Bytes32>>() {}));
    final String encoded = FunctionEncoder.encode(function);
    final Transaction tx = Transaction.createEthCallTransaction(CALLER_ADDRESS, viewsAddress, encoded);
    final EthCall response = callWithFallback(network, tx);
    final List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    final List<Bytes32> raw = ((DynamicArray<Bytes32>) decoded.get(0)).getValue();
    final List<String> result = new ArrayList<>(raw.size());
    for (final Bytes32 b : raw) {
      result.add(Numeric.toHexString(b.getValue()));
    }
    return result;
  }

  /** The real-token members currently backing a composite (unchanged during a pending decouple's window). */
  @SuppressWarnings("unchecked")
  public static List<String> getCompositeTokens(final String network, final String viewsAddress, final String compositeId) throws IOException {
    final Function function = new Function("getCompositeTokens",
        List.of(new Bytes32(Numeric.hexStringToByteArray(compositeId))),
        List.of(new TypeReference<DynamicArray<Address>>() {}));
    final String encoded = FunctionEncoder.encode(function);
    final Transaction tx = Transaction.createEthCallTransaction(CALLER_ADDRESS, viewsAddress, encoded);
    final EthCall response = callWithFallback(network, tx);
    final List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    final List<Address> raw = ((DynamicArray<Address>) decoded.get(0)).getValue();
    final List<String> result = new ArrayList<>(raw.size());
    for (final Address a : raw) {
      result.add(a.getValue());
    }
    return result;
  }

  /** 0 = ACTIVE (postable AND withdrawable), 1 = PROVISIONING (postable only — still seasoning). */
  public static int getCompositeStatus(final String network, final String viewsAddress, final String compositeId) throws IOException {
    final Function function = new Function("getCompositeStatus",
        List.of(new Bytes32(Numeric.hexStringToByteArray(compositeId))),
        List.of(new TypeReference<Uint8>() {}));
    final String encoded = FunctionEncoder.encode(function);
    final Transaction tx = Transaction.createEthCallTransaction(CALLER_ADDRESS, viewsAddress, encoded);
    final EthCall response = callWithFallback(network, tx);
    final List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    return ((Uint8) decoded.get(0)).getValue().intValue();
  }

  /**
   * Reads the public compositeConfig(bytes32) auto-getter on the CORE contract — struct field
   * order: exists, tokenCount, status, profitInclusive. Only the 4th field is needed here; the
   * other three are already available via getCompositeStatus/getCompositeTokens where relevant.
   */
  public static boolean getCompositeProfitInclusive(final String network, final String coreAddress, final String compositeId) throws IOException {
    final Function function = new Function("compositeConfig",
        List.of(new Bytes32(Numeric.hexStringToByteArray(compositeId))),
        Arrays.asList(
            new TypeReference<Bool>() {},    // exists
            new TypeReference<Uint16>() {},  // tokenCount
            new TypeReference<Uint8>() {},   // status
            new TypeReference<Bool>() {}     // profitInclusive
        ));
    final String encoded = FunctionEncoder.encode(function);
    final Transaction tx = Transaction.createEthCallTransaction(CALLER_ADDRESS, coreAddress, encoded);
    final EthCall response = callWithFallback(network, tx);
    final List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    if (decoded.size() < 4) {
      throw new IllegalStateException("FundingContractV6Loader: compositeConfig at " + coreAddress
          + " returned only " + decoded.size() + " field(s) — expected 4 (exists, tokenCount, status, "
          + "profitInclusive). Is this a pre-profitInclusive FundingContractV6 deployment at that address?");
    }
    return (Boolean) decoded.get(3).getValue();
  }

  /**
   * Reads the public `membershipProposals(bytes32)` auto-getter on the CORE contract (not
   * Views) — struct field order: compositeId, successorComposite, token, eta, op, executed. Used
   * by the auto-execute step; the caller already knows proposalId from the
   * CouplingProposed/DecouplingProposed event it is tracking (see FundManagerV2SyncJob).
   */
  public static MembershipProposal getMembershipProposal(final String network, final String coreAddress, final String proposalId) throws IOException {
    final Function function = new Function("membershipProposals",
        List.of(new Bytes32(Numeric.hexStringToByteArray(proposalId))),
        Arrays.asList(
            new TypeReference<Bytes32>() {},  // compositeId
            new TypeReference<Bytes32>() {},  // successorComposite
            new TypeReference<Address>() {},  // token
            new TypeReference<Uint48>() {},   // eta
            new TypeReference<Uint8>() {},    // op
            new TypeReference<Bool>() {}      // executed
        ));
    final String encoded = FunctionEncoder.encode(function);
    final Transaction tx = Transaction.createEthCallTransaction(CALLER_ADDRESS, coreAddress, encoded);
    final EthCall response = callWithFallback(network, tx);
    final List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    return new MembershipProposal(
        Numeric.toHexString(((Bytes32) decoded.get(0)).getValue()),
        Numeric.toHexString(((Bytes32) decoded.get(1)).getValue()),
        ((Address) decoded.get(2)).getValue(),
        ((BigInteger) decoded.get(3).getValue()).longValue(),
        ((BigInteger) decoded.get(4).getValue()).intValue(),
        (Boolean) decoded.get(5).getValue()
    );
  }

  /**
   * Reads the public `mergeProposals(bytes32)` auto-getter on the CORE contract (not Views) —
   * struct field order: targetComposite, eta, executed. Used by the auto-execute step; the caller
   * already knows proposalId from the MergeProposed event it is tracking (see
   * FundManagerV2SyncJob).
   */
  public static MergeProposal getMergeProposal(final String network, final String coreAddress, final String proposalId) throws IOException {
    final Function function = new Function("mergeProposals",
        List.of(new Bytes32(Numeric.hexStringToByteArray(proposalId))),
        Arrays.asList(
            new TypeReference<Bytes32>() {},  // targetComposite
            new TypeReference<Uint48>() {},   // eta
            new TypeReference<Bool>() {}      // executed
        ));
    final String encoded = FunctionEncoder.encode(function);
    final Transaction tx = Transaction.createEthCallTransaction(CALLER_ADDRESS, coreAddress, encoded);
    final EthCall response = callWithFallback(network, tx);
    final List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    return new MergeProposal(
        Numeric.toHexString(((Bytes32) decoded.get(0)).getValue()),
        ((BigInteger) decoded.get(1).getValue()).longValue(),
        (Boolean) decoded.get(2).getValue()
    );
  }

  /**
   * Resolves a composite member's token address to the engine's instrument id, so the V1
   * one-pass ignore-set (see FundManagerV2SnapUpdater.loadFromSnapComposite) can be built as
   * "every known stablecoin instrument NOT in this composite" instead of the old hardcoded
   * per-network MAINNET/XDC branch. The ERC20 branches are deliberately as brittle as the
   * existing Context.getTokenAddressBySymbol reverse direction they mirror — a real new
   * stablecoin's contract address isn't derivable from anything on-chain, so onboarding one
   * already requires a code change here today (Context.getUsdcContract() etc. are still a
   * fixed, small, hand-maintained set). The zero address is different: it's the protocol-wide
   * sentinel for "this chain's native coin", not a per-deployment value, so it resolves via the
   * same live security-definition feed (InstrumentCache) the rest of the engine already uses —
   * no per-network config needed, same as composite membership itself is discovered on-chain
   * rather than configured.
   */
  public static int resolveInstrumentId(final String tokenAddress) {
    if (equalsIgnoreCase(tokenAddress, com.solfini.common.Context.getUsdcContract())) return com.solfini.common.Context.getUsdcId();
    if (equalsIgnoreCase(tokenAddress, com.solfini.common.Context.getUsdtContract())) return com.solfini.common.Context.getUsdtId();
    if (equalsIgnoreCase(tokenAddress, com.solfini.common.Context.getXusdcContract())) return com.solfini.common.Context.getXusdcId();
    if (equalsIgnoreCase(tokenAddress, com.solfini.common.Context.getXusdtContract())) return com.solfini.common.Context.getXusdtId();
    if (equalsIgnoreCase(tokenAddress, org.web3j.abi.datatypes.Address.DEFAULT.toString())) {
      final com.solfini.instrument.Instrument nativeInstrument =
          com.solfini.instrument.InstrumentCache.getBySymbol(com.solfini.common.Constants.XDC);
      if (nativeInstrument == null) {
        throw new IllegalStateException("FundingContractV6Loader: native-coin composite member seen but no \""
            + com.solfini.common.Constants.XDC + "\" ASSET instrument is registered in InstrumentCache yet — "
            + "the security-definition feed must define it before this composite can publish.");
      }
      return nativeInstrument.getId();
    }
    throw new IllegalStateException("FundingContractV6Loader: unrecognized composite member token address "
        + tokenAddress + " — Context has no known instrument id for it; add it alongside the other "
        + "getXxxContract()/getXxxId() pairs before this asset can back a composite the engine publishes to.");
  }

  private static boolean equalsIgnoreCase(final String a, final String b) {
    return a != null && b != null && a.equalsIgnoreCase(b);
  }
}

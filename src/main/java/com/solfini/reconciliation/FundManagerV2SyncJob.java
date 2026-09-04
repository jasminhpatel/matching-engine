package com.solfini.reconciliation;

import static com.solfini.common.Constants.MAINNET;
import static com.solfini.common.Constants.XDC;

import com.solfini.common.Context;
import com.solfini.util.MailUtil;
import com.solfini.util.PropertyReader;
import com.solfini.util.blockchain.BlockchainSenderFactory;
import com.solfini.util.blockchain.BlockchainTransactionSender;
import com.solfini.util.blockchain.model.MembershipExecuteTransaction;
import com.solfini.util.blockchain.util.RpcUtil;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.mail.MessagingException;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

/**
 * The production sync entry point. Business/ops calls this generation "V2" (FUND_MANAGER_V2_
 * CONTRACT_ADDRESS / XDC_FUND_MANAGER_V2_CONTRACT_ADDRESS in prod config, this class name,
 * reconcile_fund_manager_v2.sh) regardless of which Solidity contract version backs it — this is
 * FundingContractV6 now, driven composite-aware instead of the old hardcoded (network, symbol)
 * list. No new "_V6"-flavored config exists anywhere in this path: once ops points the EXISTING
 * FUND_MANAGER_V2_CONTRACT_ADDRESS / XDC_FUND_MANAGER_V2_CONTRACT_ADDRESS properties at the new
 * deployment addresses, this class works unchanged — same shell script, same class name, same
 * property keys. See FundingContractV6Loader's header for how the Views companion address is
 * resolved (on-chain, not configured) and FundManagerV2SnapUpdater.updateComposite for the
 * publish path itself.
 *
 * V5's old hardcoded-symbol path (update()/contractVersion 1&amp;2, still in
 * FundManagerV2SnapUpdater) is no longer called from here — this is a full cutover, not a dual
 * run. That code is kept as library code for the planned one-off V5 fund-sweep script, not
 * because anything here still depends on it running on a schedule.
 */
public class FundManagerV2SyncJob {
  private static final String[] NETWORKS = { MAINNET, XDC };

  public FundManagerV2SyncJob() {
  }

  public static void main(String[] args) {
    final SnapContext snapContext;
    try {
      snapContext = FundManagerV2SnapUpdater.resolveSnapContext(args);
    } catch (Exception e) {
      final StringBuilder summary = new StringBuilder();
      summary.append("Failed to resolve snapshot context (affects all chains):\n").append(e.getMessage());
      try {
        String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
        MailUtil.sendMessage(to, "Blockchain–User Withdrawable Reconciliation Failed.", summary.toString(), null);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      e.printStackTrace();
      System.out.println(summary);
      System.exit(1);
      return;
    }

    for (final String network : NETWORKS) {
      final StringBuilder summary = new StringBuilder();
      try {
        syncComposites(args, network, snapContext, summary);
      } catch (Exception e) {
        e.printStackTrace();
        summary.append("Composite discovery/publish failed for network ").append(network).append(": ").append(e.getMessage()).append("\n");
        try {
          sendFailureEmail(summary, network);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
      try {
        autoExecuteMaturedProposals(network, summary);
      } catch (Exception e) {
        e.printStackTrace();
        summary.append("Auto-execute pass failed for network ").append(network).append(": ").append(e.getMessage()).append("\n");
      }
      System.out.println(summary);
    }
    System.exit(0);
  }

  private static void syncComposites(final String[] args, final String network, final SnapContext snapContext,
      final StringBuilder summary) throws IOException {
    final String coreAddress = Context.getFundManagerContractByNetworkAndVersion(network, 2);
    final String viewsAddress = FundingContractV6Loader.getViewsContractAddress(network, coreAddress);
    final List<String> compositeIds = FundingContractV6Loader.getAllComposites(network, viewsAddress);
    final Set<String> excludedComposites = loadExcludedComposites();
    // ENVIRONMENT logged alongside network/contract so a misconfigured box (e.g. real mainnet
    // address reached while ENVIRONMENT=TEST, or vice versa) is loud instead of silently wrong.
    summary.append(network).append(" [ENVIRONMENT=").append(Context.getEnvironment()).append("]: discovered ")
        .append(compositeIds.size()).append(" composite(s) at ").append(coreAddress).append(".\n");

    for (final String compositeId : compositeIds) {
      // Composite membership existing on-chain does not by itself mean the engine is ready to
      // publish withdrawable balances for it — e.g. a Segregated composite backed by an asset
      // that is ALSO a live tradeable instrument elsewhere (a perpetual swap, a spot pair) needs
      // its Position/Instrument mapping verified safe before its balances go on-chain. compositeId
      // is not chain-salted (see FundManagerV2SnapUpdater.updateComposite's contractKey comment),
      // so the key here must be network-qualified or two chains' identically-numbered composites
      // could collide.
      if (excludedComposites.contains(compositeKey(network, compositeId))) {
        summary.append(network).append("/").append(compositeId).append(" status: SKIPPED (excluded via EXCLUDE_COMPOSITE_LIST)\n");
        continue;
      }
      final List<String> memberTokens = FundingContractV6Loader.getCompositeTokens(network, viewsAddress, compositeId);
      if (memberTokens.isEmpty()) {
        // A retired composite (executeCouple's re-home retire, or invalidateProposal retiring a
        // cancelled decouple's successor) — exists may still read true on the struct but its
        // token list is empty; nothing to publish.
        continue;
      }
      final StringBuilder compositeSummary = new StringBuilder();
      try {
        boolean success = FundManagerV2SnapUpdater.updateComposite(args, compositeSummary, null, network, coreAddress, compositeId, memberTokens, snapContext);
        summary.append(network).append("/").append(compositeId).append(" status: ").append(success).append("\n");
      } catch (Exception e) {
        e.printStackTrace();
        summary.append(network).append("/").append(compositeId).append(" FAILED: ").append(e.getMessage()).append("\n");
      }
      System.out.println(compositeSummary);

      // NOTE (TSYS-176): reconciliation deliberately does NOT run here. This job only publishes —
      // reconciliation is event-driven: the publish above should cause the contract to emit
      // CompositeBalanceUpdated, and it's that real on-chain event (observed by the
      // blockchain-listener, TSYS-100/TSYS-177) that triggers FundManagerV2ReconcileTriggerJob for
      // this composite, not this cron's own cadence. See FundManagerV2ReconcileTriggerJob's header
      // comment for the on-demand entry point this replaced.
    }
  }

  /**
   * EXCLUDE_COMPOSITE_LIST — comma-separated {@code NETWORK:compositeId} entries (case-insensitive),
   * e.g. {@code XDC:0x310419c27d3c8ae886001cfc02a49fcc9b6b7334297cd6bf93571bfeead5b12c}. Defaults to
   * empty (nothing excluded, existing behaviour unchanged). Mirrors the existing per-symbol
   * IGNORE_SYMBOL_LIST pattern (see FundManagerV2SnapUpdater), but at the whole-composite level:
   * a composite can exist on-chain (registered, discoverable) without the engine being cleared to
   * publish balances for it — e.g. pending confirmation that its backing asset's Instrument mapping
   * is genuinely deposit-only and not shared with a live tradeable market.
   */
  private static Set<String> loadExcludedComposites() {
    final String raw = PropertyReader.getProperty("EXCLUDE_COMPOSITE_LIST", "");
    final Set<String> excluded = new HashSet<>();
    if (raw != null && !raw.isBlank()) {
      for (final String entry : raw.split(",")) {
        final String trimmed = entry.trim();
        if (!trimmed.isEmpty()) {
          excluded.add(trimmed.toLowerCase());
        }
      }
    }
    return excluded;
  }

  private static String compositeKey(final String network, final String compositeId) {
    return (network + ":" + compositeId).toLowerCase();
  }

  // ─── Auto-execute ────────────────────────────────────────────────────────────
  // Deliberately simple: a bounded recent-block eth_getLogs scan, not a persisted-checkpoint
  // event indexer (this codebase has no prior event-consumer infrastructure to build on). A
  // missed/late auto-execute is an operational delay (picked up next run, or triggered manually),
  // NOT a fund-safety issue — the on-chain shrunk-until-next-publish marker protects correctness
  // regardless of when execute actually lands. Widen MEMBERSHIP_EXECUTE_LOOKBACK_BLOCKS_<NETWORK>
  // if proposals are ever expected to mature further apart than the default window covers.
  private static void autoExecuteMaturedProposals(final String network, final StringBuilder summary) throws IOException {
    final String coreAddress = Context.getFundManagerContractByNetworkAndVersion(network, 2);
    final int lookbackBlocks = PropertyReader.getProperty("MEMBERSHIP_EXECUTE_LOOKBACK_BLOCKS_" + network.toUpperCase(), 200_000);

    final Web3j web3j = RpcUtil.createWeb3jConnection(network, null, false, false);
    final BigInteger latest = web3j.ethBlockNumber().send().getBlockNumber();
    final BigInteger from = latest.subtract(BigInteger.valueOf(lookbackBlocks)).max(BigInteger.ZERO);

    final Set<String> proposalIds = new HashSet<>();
    final Map<String, MembershipExecuteTransaction.MembershipOp> opByProposalId = new HashMap<>();
    scanProposedEvents(web3j, coreAddress, from, latest, MembershipExecuteTransaction.MembershipOp.COUPLE, proposalIds, opByProposalId);
    scanProposedEvents(web3j, coreAddress, from, latest, MembershipExecuteTransaction.MembershipOp.DECOUPLE, proposalIds, opByProposalId);
    scanMergeProposedEvents(web3j, coreAddress, from, latest, proposalIds, opByProposalId);

    // Drop anything already resolved (executed or invalidated) in the same window — cheaper to
    // filter here than to fire a doomed execute and let it revert. Each call needs the event's
    // FULL parameter type list, not just proposalId's Bytes32: topic0 (what EthFilter actually
    // matches on) is keccak256 of the event's complete canonical signature — every parameter,
    // indexed or not — so a declared Event with fewer params than the real one computes the
    // WRONG topic0 and silently matches nothing. (Fixed here: the previous minimal single-Bytes32
    // declaration meant this resolved-proposal filter was a no-op for all three existing events —
    // harmless since a redundant executeCouple/executeDecouple on an already-executed proposal
    // just reverts on-chain, but worth fixing rather than extending the same mistake to Merge.)
    removeResolved(web3j, coreAddress, from, latest, "CouplingExecuted", List.of(
        new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {},
        new TypeReference<Address>(true) {}, new TypeReference<Uint16>(false) {}), proposalIds);
    removeResolved(web3j, coreAddress, from, latest, "DecouplingExecuted", List.of(
        new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {},
        new TypeReference<Address>(true) {}, new TypeReference<Bytes32>(false) {}, new TypeReference<Uint16>(false) {}), proposalIds);
    removeResolved(web3j, coreAddress, from, latest, "MembershipProposalCancelled", List.of(
        new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {},
        new TypeReference<Address>(true) {}, new TypeReference<Address>(false) {}), proposalIds);
    removeResolved(web3j, coreAddress, from, latest, "MergeExecuted", List.of(
        new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {}), proposalIds);
    removeResolved(web3j, coreAddress, from, latest, "MergeInvalidated", List.of(
        new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(false) {}), proposalIds);

    final long nowEpochSeconds = System.currentTimeMillis() / 1000L;
    for (final String proposalId : proposalIds) {
      try {
        final MembershipExecuteTransaction.MembershipOp op = opByProposalId.get(proposalId);
        final boolean matured = op == MembershipExecuteTransaction.MembershipOp.MERGE
            ? FundingContractV6Loader.getMergeProposal(network, coreAddress, proposalId).isMatured(nowEpochSeconds)
            : FundingContractV6Loader.getMembershipProposal(network, coreAddress, proposalId).isMatured(nowEpochSeconds);
        if (!matured) {
          continue;
        }
        final MembershipExecuteTransaction tx = new MembershipExecuteTransaction();
        tx.setProposalId(proposalId);
        tx.setOp(op);
        tx.setChainType(network.toUpperCase());
        tx.setContractAddress(coreAddress);
        tx.setId(nowEpochSeconds);
        final BlockchainTransactionSender sender = BlockchainSenderFactory.getSender(tx);
        final boolean status = sender != null && sender.processTransaction(summary);
        summary.append(network).append(": auto-execute proposalId ").append(proposalId).append(" op=").append(op)
            .append(" status: ").append(status).append("\n");
      } catch (Exception e) {
        e.printStackTrace();
        summary.append(network).append(": auto-execute FAILED for proposalId ").append(proposalId).append(": ").append(e.getMessage()).append("\n");
      }
    }
  }

  private static void scanProposedEvents(final Web3j web3j, final String coreAddress, final BigInteger from, final BigInteger to,
      final MembershipExecuteTransaction.MembershipOp op, final Set<String> proposalIds,
      final Map<String, MembershipExecuteTransaction.MembershipOp> opByProposalId) throws IOException {
    // event CouplingProposed(bytes32 indexed proposalId, bytes32 indexed compositeId, address indexed token, uint256 eta)
    // event DecouplingProposed(bytes32 indexed proposalId, bytes32 indexed compositeId, address indexed token, uint256 eta)
    // proposalId is the first indexed (topic[1]) param on both — that's all this scan needs.
    final boolean decouple = op == MembershipExecuteTransaction.MembershipOp.DECOUPLE;
    final Event event = new Event(decouple ? "DecouplingProposed" : "CouplingProposed",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {},
            new TypeReference<Address>(true) {}, new TypeReference<Uint256>(false) {}));
    final EthFilter filter = new EthFilter(DefaultBlockParameter.valueOf(from), DefaultBlockParameter.valueOf(to), coreAddress);
    filter.addSingleTopic(EventEncoder.encode(event));
    final EthLog ethLog = web3j.ethGetLogs(filter).send();
    for (final EthLog.LogResult<?> result : ethLog.getLogs()) {
      final Log log = (Log) result.get();
      if (log.getTopics().size() > 1) {
        final String proposalId = log.getTopics().get(1);
        proposalIds.add(proposalId);
        opByProposalId.put(proposalId, op);
      }
    }
  }

  // event MergeProposed(bytes32 indexed proposalId, bytes32 indexed targetComposite, uint256 eta)
  // proposalId is the first indexed (topic[1]) param — same extraction shape as scanProposedEvents,
  // kept as its own method since MergeProposed's parameter list genuinely differs (3 params, not 4).
  private static void scanMergeProposedEvents(final Web3j web3j, final String coreAddress, final BigInteger from, final BigInteger to,
      final Set<String> proposalIds, final Map<String, MembershipExecuteTransaction.MembershipOp> opByProposalId) throws IOException {
    final Event event = new Event("MergeProposed",
        List.of(new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {}, new TypeReference<Uint256>(false) {}));
    final EthFilter filter = new EthFilter(DefaultBlockParameter.valueOf(from), DefaultBlockParameter.valueOf(to), coreAddress);
    filter.addSingleTopic(EventEncoder.encode(event));
    final EthLog ethLog = web3j.ethGetLogs(filter).send();
    for (final EthLog.LogResult<?> result : ethLog.getLogs()) {
      final Log log = (Log) result.get();
      if (log.getTopics().size() > 1) {
        final String proposalId = log.getTopics().get(1);
        proposalIds.add(proposalId);
        opByProposalId.put(proposalId, MembershipExecuteTransaction.MembershipOp.MERGE);
      }
    }
  }

  private static void removeResolved(final Web3j web3j, final String coreAddress, final BigInteger from, final BigInteger to,
      final String eventName, final List<TypeReference<?>> parameterTypes, final Set<String> proposalIds) throws IOException {
    if (proposalIds.isEmpty()) {
      return;
    }
    final Event event = new Event(eventName, parameterTypes);
    final EthFilter filter = new EthFilter(DefaultBlockParameter.valueOf(from), DefaultBlockParameter.valueOf(to), coreAddress);
    filter.addSingleTopic(EventEncoder.encode(event));
    final EthLog ethLog = web3j.ethGetLogs(filter).send();
    for (final EthLog.LogResult<?> result : ethLog.getLogs()) {
      final Log log = (Log) result.get();
      if (log.getTopics().size() > 1) {
        proposalIds.remove(log.getTopics().get(1));
      }
    }
  }

  private static void sendFailureEmail(final StringBuilder summary, final String network) throws MessagingException, IOException {
    final String networkLabel = FundManagerV2Reconciliation.networkLabel(network);
    summary.insert(0, networkLabel + "–User Withdrawable Sync Job Failed:\n");
    String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
    String subject = networkLabel + "–User Withdrawable Reconciliation Failed.";
    String body = summary.toString();

    MailUtil.sendMessage(to, subject, body, null);
  }
}

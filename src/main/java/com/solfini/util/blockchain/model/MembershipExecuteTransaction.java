package com.solfini.util.blockchain.model;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

/**
 * A single executeCouple(bytes32)/executeDecouple(bytes32)/executeMerge(bytes32) call on
 * FundingContractV6 — signed and sent by the same FUND_MANAGEMENT (balance-poster) role that
 * publishes balances, matching the on-chain onlyBalancePoster gate on all three functions (see
 * docs/eth-fundingcontract-v6-timelock-and-engine-2026-07-19.md §3.4/§5 Q4 in the
 * quote.trade-contracts repo — execute is deliberately tied to the same operational key, not a
 * separate admin/permissionless path).
 */
public class MembershipExecuteTransaction extends BlockchainTransaction implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MembershipExecuteTransaction.class);

  /** Which execute* function to call — all three take the identical single bytes32 proposalId
   * argument, just against a different on-chain proposal registry/function name. */
  public enum MembershipOp {
    COUPLE, DECOUPLE, MERGE
  }

  private String proposalId;
  private MembershipOp op;

  public String getProposalId() {
    return proposalId;
  }

  public void setProposalId(String proposalId) {
    this.proposalId = proposalId;
  }

  public MembershipOp getOp() {
    return op;
  }

  public void setOp(MembershipOp op) {
    this.op = op;
  }
}

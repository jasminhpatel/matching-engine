package com.solfini.reconciliation;

/**
 * Snapshot context resolved once at the start of a multi-chain sync run and shared
 * across all chains, so every chain uses the SAME completed snapshot folder and the
 * SAME blockchain_snap_mapping id.
 *
 * @param snapFolder absolute path to the completed snapshot folder (has a 'done' file)
 * @param snapId     numeric snapshot id (the folder name as a long)
 * @param mappingId  incremental id from blockchain_snap_mapping for snapId
 */
public record SnapContext(String snapFolder, long snapId, long mappingId) {
}

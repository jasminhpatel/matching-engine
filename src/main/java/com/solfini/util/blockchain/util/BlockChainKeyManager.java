package com.solfini.util.blockchain.util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The KeyReader class allows program code to load encrypted blockchain keys required to instance id.
 * file should contain a list of encrypted values as follows
 * <network>:<function>:<public key>
 */
public class BlockChainKeyManager {
  private static final ConcurrentHashMap<String, ArrayList<String>> CHAIN_FUNCTION_TO_PUBLIC_KEY_MAP = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Object> CHAIN_ADDRESS_TO_LOCK_MAP = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, AtomicInteger> CHAIN_TO_COUNTER = new ConcurrentHashMap<>();
  private static final int INDEX_OF_CHAIN_TYPE = 0;
  private static final int INDEX_OF_FUNCTION = 1;
  private static final int INDEX_OF_PUBLIC_ADDRESS = 2;

  private BlockChainKeyManager() {
    // hidden default constructor
  }

  /**
   * Reads the encrypted blockchain key file
   *
   * @param filename path to the encrypted keyfile.
   * @throws FileNotFoundException Thrown if the specified property file is not found.
   * @throws IOException           Throws if the property file loading failed.
   */
  public static final void loadKeys(final String filename) throws IOException, FileNotFoundException {
    try (final BufferedReader reader = new BufferedReader(new FileReader(filename))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.length() > 0 && !line.startsWith("#")) {
          //add <chain>_<function> <address> mapping
          final String[] values = line.split(":");
          final String key = values[INDEX_OF_CHAIN_TYPE].toUpperCase() + "_" + values[INDEX_OF_FUNCTION].toUpperCase();
          final ArrayList<String> chainFunctionAddresses = CHAIN_FUNCTION_TO_PUBLIC_KEY_MAP.computeIfAbsent(key, v -> new ArrayList<>());
          chainFunctionAddresses.add(values[INDEX_OF_PUBLIC_ADDRESS]);

          //add lock objects
          final String lockKey = values[INDEX_OF_CHAIN_TYPE].toUpperCase() + "_" + values[INDEX_OF_PUBLIC_ADDRESS].toUpperCase();
          final Object lock = CHAIN_ADDRESS_TO_LOCK_MAP.computeIfAbsent(lockKey, v -> new Object());
        }
      }
    }
    // print stats
    printKeys();
  }
  /**
   * Returns the next available system address systemAddress.
   *
   * @param chainType POLYGON, ETHEREUM, ...
   * @param function RETIRE_FROM, MINT, ....
   */
  private static String normalizeChainType(final String chainType) {
    return "MAINNET".equalsIgnoreCase(chainType) ? "ETHEREUM" : chainType.toUpperCase();
  }

  public static final String getNextKey(final String chainType, final String function) {
    printKeys();
    String key = normalizeChainType(chainType) + "_" + function.toUpperCase();
    //System.out.printf("Key: " + key);
    final ArrayList<String> functionKeys = CHAIN_FUNCTION_TO_PUBLIC_KEY_MAP.get(key);
    if (functionKeys == null) {
      return null;
    }

    final AtomicInteger counter = CHAIN_TO_COUNTER.computeIfAbsent(key, v -> new AtomicInteger(RandomUtil.generateInt(functionKeys.size())));
    return functionKeys.get(counter.incrementAndGet() % functionKeys.size());
  }

  /**
   * Returns a lock object for the systemAddress.
   *
   * @param chainType POLYGON, ETHEREUM, ...
   * @param publicKey public key used to submit transaction.
   */
  public static final Object getLockObject(final String chainType, final String publicKey) {
    final String lockKey = normalizeChainType(chainType) + "_" + publicKey.toUpperCase();
    return CHAIN_ADDRESS_TO_LOCK_MAP.get(lockKey);
  }

  private static void printKeys() {
    if (!CHAIN_FUNCTION_TO_PUBLIC_KEY_MAP.isEmpty()) {
      for (Map.Entry<String, ArrayList<String>> entry : CHAIN_FUNCTION_TO_PUBLIC_KEY_MAP.entrySet()) {
        System.out.println("No of " + entry.getKey() + " keys : " + entry.getValue().size());
      }
    } else {
      System.out.println("Zero keys loaded.");
    }
  }

  public static void main(String[] args) throws IOException {
    loadKeys("./config/test/blockchain_keys.txt");
  }

}

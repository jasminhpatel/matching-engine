package com.solfini.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author Chris Mack
 *
 */
public class ProcessMonitor {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessMonitor.class);

  private ProcessMonitor() {
    // Private constructor
  }

  public static int getStartProcessId() {
    int rc = 0;
    try {
      Runtime rt = Runtime.getRuntime();
      String[] cmdString = {"/bin/sh", "-c", "ps -ef | grep java | grep MatchEngineStarter | awk '{print $2}'"};
      Process process = rt.exec(cmdString);
      BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line = null;
      if ((line = input.readLine()) != null) {
        rc = Integer.parseInt(line.trim());
      }
      input.close();
    } catch (Exception e) {
      LOGGER.error("Error in getStartProcessId", e);
    }
    return rc;
  }

  public static List<String> getHisto(final int processId) {
    final List<String> list = new FastArrayList<>();
    try {
      Runtime rt = Runtime.getRuntime();
      String[] cmdString = {"/bin/sh", "-c", "jmap -histo " + processId};
      Process process = rt.exec(cmdString);
      BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line = null;
      while ((line = input.readLine()) != null) {
        list.add(line.trim());
      }
      input.close();
    } catch (Exception e) {
      LOGGER.error("Error in getStartProcessId", e);
    }
    return list;
  }

  public static boolean killProcessId(final int processId) {
    boolean rc = true;
    try {
      Runtime rt = Runtime.getRuntime();
      String[] cmdString = {"/bin/sh", "-c", "kill -9 " + processId};
      rt.exec(cmdString);
    } catch (Exception e) {
      LOGGER.error("Error in ProcessMonitor", e);
    }
    return rc;
  }
}

package com.solfini.util;

import java.util.List;

public class ProcessMonitorTest {

  public static void main(String[] args) {
    int processId = ProcessMonitor.getStartProcessId();

    System.out.println("processId=" + processId);

    List<String> histo = ProcessMonitor.getHisto(processId);
    for (int i = 0; i < histo.size(); i++) {
      if (i > 60 && i < histo.size() - 1)
        continue;
      System.out.println("histo" + i + "=" + histo.get(i));
    }
  }
}

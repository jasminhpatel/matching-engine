package com.solfini;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class SingleTestRunner {
  public static void main(String[] args) throws ClassNotFoundException {
    if (args.length != 1) {
      System.out.println("Usage: SingleTestRunner <TestClass#TestMethod>");
      return;
    }

    String[] tokens = args[0].split("#");
    System.out.println("RUNNING: " + args[0]);

    Request request = Request.method(Class.forName(tokens[0]), tokens[1]);
    Result result = new JUnitCore().run(request);
    if (!result.wasSuccessful()) {
      System.out.println("FAILED");
      for (Failure failure : result.getFailures()) {
        System.out.println(failure.getDescription());
        System.out.println(failure.getMessage());
        failure.getException().printStackTrace();
        System.out.println();
      }

      System.exit(1);
    }

    System.out.println("OK");
    System.exit(0);
  }
}

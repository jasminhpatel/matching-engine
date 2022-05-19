package com.solfini.util;

public class CodeCoverageMethod {
  public CodeCoverageMethod() {
    // do nothing
  }

  @CodeCoverageExclude.Generated
  public boolean foo() {
    System.out.println("foo() called");
    return true;
  }

  public boolean bar() {
    System.out.println("bar() called");
    return true;
  }
}

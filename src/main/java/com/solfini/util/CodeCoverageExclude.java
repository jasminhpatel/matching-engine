package com.solfini.util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Annotation to exclude classes/methods from code coverage report.
public class CodeCoverageExclude {
  @Retention(RetentionPolicy.CLASS)
  public @interface Generated {
    // do nothing
  }
}

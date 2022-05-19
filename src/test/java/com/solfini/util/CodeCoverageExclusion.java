package com.solfini.util;

import org.junit.Assert;
import org.junit.Test;

public class CodeCoverageExclusion {

    @Test
    public void testExcludedClass() {
        CodeCoverageClass inst = new CodeCoverageClass();
        Assert.assertTrue(inst.foo());
    }

    @Test
    public void testExcludedMethod() {
        CodeCoverageMethod inst = new CodeCoverageMethod();
        Assert.assertTrue(inst.foo());
    }

    @Test
    public void testNotExcludedMethod() {
        CodeCoverageMethod inst = new CodeCoverageMethod();
        Assert.assertTrue(inst.bar());
    }

    public static void main(String[] args) {
        final CodeCoverageExclusion test = new CodeCoverageExclusion();
        test.testExcludedClass();
        test.testExcludedMethod();
        test.testNotExcludedMethod();
    }
}

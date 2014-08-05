package org.threadly.util;

import static org.junit.Assert.*;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class ArgumentVerifierTest {
  @Test
  public void assertNotNullTest() {
    ArgumentVerifier.assertNotNull(new Object(), "foo");
    // should not throw
  }
  
  @Test
  public void assertNotNullFail() {
    String name = "foo";
    try {
      ArgumentVerifier.assertNotNull(null, "foo");
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains(name));
    }
  }
  
  @Test
  public void assertNotNegativeTest() {
    ArgumentVerifier.assertNotNegative(0, "foo");
    // should not throw
  }
  
  @Test
  public void assertNotNegativeFail() {
    String name = "foo";
    try {
      ArgumentVerifier.assertNotNegative(-1, "foo");
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains(name));
    }
  }
  
  @Test
  public void assertGreaterThanZeroTest() {
    ArgumentVerifier.assertGreaterThanZero(1, "foo");
    // should not throw
  }
  
  @Test
  public void assertGreaterThanZeroFail() {
    String name = "foo";
    try {
      ArgumentVerifier.assertGreaterThanZero(0, "foo");
      fail("Exception should have thrown");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains(name));
    }
  }
}

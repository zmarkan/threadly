package org.threadly.concurrent;

import org.threadly.test.concurrent.TestRunnable;

@SuppressWarnings("javadoc")
public class TestRuntimeFailureRunnable extends TestRunnable {
  private final RuntimeException toThrowException;
  
  public TestRuntimeFailureRunnable() {
    this(0, null);
  }

  public TestRuntimeFailureRunnable(int sleep) {
    this(sleep, null);
  }
  
  public TestRuntimeFailureRunnable(RuntimeException toThrowException) {
    this(0, toThrowException);
  }
  
  public TestRuntimeFailureRunnable(int sleep, RuntimeException toThrowException) {
    super(sleep);
    
    this.toThrowException = toThrowException;
  }

  @Override
  public void handleRunFinish() {
    if (toThrowException != null) {
      throw toThrowException;
    } else {
      throw new RuntimeException();
    }
  }
}
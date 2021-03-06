package org.threadly.test.concurrent;

import static org.junit.Assert.*;
import static org.threadly.TestConstants.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.BlockingTestRunnable;
import org.threadly.test.concurrent.TestRunnable;
import org.threadly.test.concurrent.TestUtils;
import org.threadly.test.concurrent.TestCondition.ConditionTimeoutException;

@SuppressWarnings("javadoc")
public class TestRunnableTest {
  private TestRunnable instance;
  
  @Before
  public void setup() {
    instance = new TestRunnable();
  }
  
  @After
  public void tearDown() {
    instance = null;
  }
  
  @Test
  public void constructorTest() {
    assertEquals(0, instance.getRunCount());
    assertFalse(instance.ranOnce());
    assertFalse(instance.ranConcurrently());
    assertEquals(0, instance.getRunDelayInMillis());
    
    instance = new TestRunnable(DELAY_TIME);
    assertEquals(DELAY_TIME, instance.getRunDelayInMillis());
  }
  
  @Test
  public void setRunDelayInMillisTest() {
    assertEquals(0, instance.getRunDelayInMillis());
    instance.setRunDelayInMillis(DELAY_TIME);
    assertEquals(DELAY_TIME, instance.getRunDelayInMillis());
  }
  
  @Test
  public void runTest() {
    TestTestRunnable ttr = new TestTestRunnable();
    long start = System.currentTimeMillis();
    
    TestUtils.blockTillClockAdvances();
    
    ttr.run();
    
    assertTrue(ttr.handleRunStartCalled);
    assertTrue(ttr.handleRunFinishCalled);
    assertTrue(ttr.startCalledBeforeFinish);
    assertTrue(ttr.ranOnce());
    assertEquals(1, ttr.getRunCount());
    assertTrue(ttr.getDelayTillFirstRun() > 0);

    TestUtils.blockTillClockAdvances();
    
    ttr.run();
    
    TestUtils.blockTillClockAdvances();
    
    long now = System.currentTimeMillis();
    assertTrue(ttr.getDelayTillRun(2) <= now - start);
    assertTrue(ttr.getDelayTillRun(2) > ttr.getDelayTillFirstRun());
  }
  
  @Test
  public void runWithDelay() {
    int runCount = TEST_QTY / 2;
    instance.setRunDelayInMillis(DELAY_TIME);
    
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < runCount; i++) {
      instance.run();
    }
    long endTime = System.currentTimeMillis();
    
    assertTrue(endTime - startTime >= (DELAY_TIME * runCount));
  }
  
  @Test
  public void blockTillRunTest() {
    TestRunnable tr = new TestRunnable() {
      private boolean firstRun = true;
      
      @Override
      public void handleRunStart() throws InterruptedException {
        if (firstRun) {
          firstRun = false;
          TestUtils.sleep(DELAY_TIME);
          run();
        }
      }
    };
    new Thread(tr).start();
    
    long startTime = System.currentTimeMillis();
    tr.blockTillFinished(1000, 2);
    long endTime = System.currentTimeMillis();
    
    assertTrue(endTime - startTime >= DELAY_TIME);
  }
  
  @Test (expected = ConditionTimeoutException.class)
  public void blockTillRunTestFail() {
    instance.blockTillFinished(DELAY_TIME);
    
    fail("Exception should have thrown");
  }
  
  @Test
  public void ranConcurrentlyTest() {
    TestRunnable notConcurrentTR = new TestRunnable();
    
    notConcurrentTR.run();
    notConcurrentTR.run();
    assertFalse(notConcurrentTR.ranConcurrently());
    
    TestRunnable concurrentTR = new TestRunnable() {
      private boolean ranOnce = false;
      
      @Override
      public void handleRunStart() {
        if (! ranOnce) {
          // used to prevent infinite recursion
          ranOnce = true;
          
          this.run();
        }
      }
    };
    
    concurrentTR.run();
    assertTrue(concurrentTR.ranConcurrently());
  }
  
  @Test
  public void currentlyRunningTest() {
    BlockingTestRunnable btr = new BlockingTestRunnable();
    
    assertFalse(btr.isRunning());
    
    new Thread(btr).start();
    try {
      btr.blockTillStarted();
      
      assertTrue(btr.isRunning());
    } finally {
      btr.unblock();
    }
  }
  
  private class TestTestRunnable extends TestRunnable {
    private boolean handleRunStartCalled = false;
    private boolean handleRunFinishCalled = false;
    private boolean startCalledBeforeFinish = false;
    
    @Override
    public void handleRunStart() {
      handleRunStartCalled = true;
    }
    
    @Override
    public void handleRunFinish() {
      handleRunFinishCalled = true;
      startCalledBeforeFinish = handleRunStartCalled;
    }
  }
}

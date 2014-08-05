package org.threadly.concurrent.collections;

import static org.junit.Assert.*;
import static org.threadly.TestConstants.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.TestDelayed;
import org.threadly.concurrent.collections.DynamicDelayQueue;

@SuppressWarnings("javadoc")
public class DynamicDelayQueueTest {
  private DynamicDelayQueue<TestDelayed> testQueue;
  
  @Before
  public void setup() {
    testQueue = new DynamicDelayQueue<TestDelayed>();
  }
  
  @After
  public void teardown() {
    testQueue = null;
  }
  
  private static void populatePositive(DynamicDelayQueue<TestDelayed> testQueue) {
    boolean flip = true;
    for (int i = 0; i < TEST_QTY; i++) {
      if (flip) {
        testQueue.add(new TestDelayed(i));
        flip = false;
      } else {
        testQueue.addLast(new TestDelayed(i));
        flip = true;
      }
    }
  }
  
  private static void populateNegative(DynamicDelayQueue<TestDelayed> testQueue) {
    boolean flip = true;
    for (int i = TEST_QTY * -1; i < 0; i++) {
      if (flip) {
        testQueue.add(new TestDelayed(i));
        flip = false;
      } else {
        testQueue.offer(new TestDelayed(i), Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        flip = true;
      }
    }
  }
  
  private static void populateRandom(DynamicDelayQueue<TestDelayed> testQueue) {
    Random random = new SecureRandom();
    boolean flip = true;
    for (int i = 0; i < TEST_QTY; i++) {
      if (flip) {
        testQueue.add(new TestDelayed(random.nextInt()));
        flip = false;
      } else {
        testQueue.offer(new TestDelayed(random.nextInt()));
        flip = true;
      }
    }
  }
  
  private static void verifyQueueOrder(DynamicDelayQueue<TestDelayed> testQueue) {
    synchronized (testQueue.getLock()) {
      long lastDelay = Long.MIN_VALUE;
      Iterator<TestDelayed> it = testQueue.iterator();
      while (it.hasNext()) {
        TestDelayed td = it.next();
        long delay = td.getDelay(TimeUnit.MILLISECONDS);
        assertTrue(delay >= lastDelay);
        lastDelay = delay;
      }
    }
  }
  
  @Test
  public void toStringTest() {
    String testStr = testQueue.toString();
    assertNotNull(testStr);
    assertTrue(testStr.length() > 1);
  }
  
  @Test
  public void blockTillAvailableTest() throws InterruptedException {
    final int delayTime = 20;
    
    long startTime = System.currentTimeMillis();
    testQueue.put(new RealTimeDelayed(delayTime));
    synchronized (testQueue.queueLock) {
      testQueue.blockTillAvailable();
    }
    long endTime = System.currentTimeMillis();
    
    assertTrue(endTime - startTime >= delayTime);
  }
  
  @Test
  public void sizeTest() {
    for (int i = 0; i < TEST_QTY; i++) {
      testQueue.add(new TestDelayed(i));
      assertEquals(i+1, testQueue.size());
    }
  }
  
  @Test
  public void isEmptyTest() throws InterruptedException {
    assertTrue(testQueue.isEmpty());
    testQueue.add(new TestDelayed(0));
    assertFalse(testQueue.isEmpty());

    testQueue.take();

    assertTrue(testQueue.isEmpty());
  }
  
  @Test
  public void containsTest() {
    for (int i = 0; i < TEST_QTY; i++) {
      testQueue.add(new TestDelayed(i));
    }
    for (int i = 0; i < TEST_QTY; i++) {
      TestDelayed td = new TestDelayed(i);
      assertTrue(testQueue.contains(td));
    }
    for (int i = TEST_QTY + 1; i < TEST_QTY * 2; i++) {
      TestDelayed td = new TestDelayed(i);
      assertFalse(testQueue.contains(td));
    }
  }
  
  @Test
  public void containsAllTest() {
    List<TestDelayed> comparisionList = new ArrayList<TestDelayed>(TEST_QTY + 1);
    assertTrue(testQueue.containsAll(comparisionList));
    assertTrue(testQueue.containsAll(comparisionList));
    comparisionList.add(new TestDelayed(Integer.MIN_VALUE));
    assertFalse(testQueue.containsAll(comparisionList));
    testQueue.add(new TestDelayed(Integer.MIN_VALUE));
    assertTrue(testQueue.containsAll(comparisionList));
    for (int i = 0; i < TEST_QTY; i++) {
      TestDelayed td = new TestDelayed(i);
      comparisionList.add(td);
      assertFalse(testQueue.containsAll(comparisionList));
      testQueue.add(td);
      assertTrue(testQueue.containsAll(comparisionList));
    }
    
    testQueue.add(new TestDelayed(Integer.MAX_VALUE));
    assertTrue(testQueue.containsAll(comparisionList));
  }
  
  @Test
  public void addNullTest() {
    assertFalse(testQueue.add(null));
  }
  
  @Test
  public void addAllTest() {
    List<TestDelayed> toAddList = new ArrayList<TestDelayed>(TEST_QTY);
    for (int i = 0; i < TEST_QTY; i++) {
      TestDelayed td = new TestDelayed(i);
      toAddList.add(td);
    }
    
    testQueue.addAll(toAddList);
    
    assertEquals(TEST_QTY, testQueue.size());
    assertTrue(testQueue.containsAll(toAddList));
    
    synchronized (testQueue.getLock()) {
      Iterator<TestDelayed> it = toAddList.iterator();
      Iterator<TestDelayed> testIt = testQueue.iterator();
      while (it.hasNext()) {
        assertTrue(it.next() == testIt.next());
      }
    }
  }
  
  @Test (expected = NullPointerException.class)
  public void addLastFail() {
    testQueue.addLast(null);
    
    fail("Exception should have been thrown");
  }
  
  @Test
  public void repositionNullTest() {
    testQueue.reposition(null, 100, new DynamicDelayedUpdater() {
      @Override
      public void allowDelayUpdate() {
        fail(); // should not be called
      }
    });
  }
  
  @Test
  public void iteratorTest() {
    synchronized (testQueue.getLock()) {
      populatePositive(testQueue);
      
      int value = 0;
      Iterator<TestDelayed> it = testQueue.iterator();
      while (it.hasNext()) {
        assertEquals(value++, it.next().getDelay(TimeUnit.MILLISECONDS));
      }
      
      it = testQueue.iterator();
      for (int i = TEST_QTY; i > 0; i--) {
        assertEquals(i, testQueue.size());
        it.next();
        it.remove();
      }
    }
  }
  
  @Test (expected = IllegalStateException.class)
  public void iteratorLockFail() {
    testQueue.iterator();
    
    fail("Exception should have thrown");
  }
  
  @Test
  public void consumerIteratorOverallTest() throws InterruptedException {
    synchronized (testQueue.getLock()) {
      populateNegative(testQueue);
      
      int removed = 0;
      ConsumerIterator<TestDelayed> it = testQueue.consumeIterator();
      while (it.hasNext()) {
        TestDelayed peek = it.peek();
        assertEquals(it.remove(), peek);
        removed++;
        assertEquals(TEST_QTY - removed, testQueue.size());
      }
      
      assertEquals(TEST_QTY, removed);
    }
  }
  
  
  @Test (expected = IllegalStateException.class)
  public void consumerIteratorLockFail() throws InterruptedException {
    testQueue.consumeIterator();
    
    fail("Exception should have thrown");
  }
   
  @Test
  public void sortTest() {
    populateRandom(testQueue);
    verifyQueueOrder(testQueue);
    // add unsorted data
    Random random = new SecureRandom();
    for (int i = 0; i < TEST_QTY; i++) {
      testQueue.addLast(new TestDelayed(random.nextInt()));
    }
    
    testQueue.sortQueue();
    
    verifyQueueOrder(testQueue);
  }
  
  @Test
  public void clearTest() {
    populatePositive(testQueue);
    assertEquals(TEST_QTY, testQueue.size());
    testQueue.clear();
    assertEquals(0, testQueue.size());
  }
  
  @Test
  public void elementTest() {
    for (int i = 0; i < TEST_QTY; i++) {
      TestDelayed item = new TestDelayed(i * -1);
      testQueue.add(item);
      assertEquals(item, testQueue.element());
    }
  }
  
  @Test (expected = NoSuchElementException.class)
  public void elementFail() {
    testQueue.element();
    
    fail("Exception should have been thrown");
  }
  
  @Test
  public void peekTest() {
    TestDelayed item = new TestDelayed(100);
    testQueue.add(item);
    assertNull(testQueue.peek()); // assert peek in future is null
    
    for (int i = 0; i < TEST_QTY; i++) {
      item = new TestDelayed(i * -1);
      testQueue.add(item);
      assertEquals(item, testQueue.peek());
    }
  }
  
  @Test
  public void pollTest() {
    populateRandom(testQueue);
    
    TestDelayed prev = testQueue.poll();
    for (int i = 0; i > TEST_QTY; i++) {
      assertTrue(prev.compareTo(testQueue.peek()) >= 0);
      
      prev = testQueue.poll();
    }
  }
  
  @Test
  public void pollTimeoutTest() throws InterruptedException {
    populateRandom(testQueue);
    
    TestDelayed prev = testQueue.poll(0, TimeUnit.MILLISECONDS);
    for (int i = 0; i > TEST_QTY; i++) {
      assertTrue(prev.compareTo(testQueue.peek()) >= 0);
      
      prev = testQueue.poll(0, TimeUnit.MILLISECONDS);
    }
  }
  
  @Test
  public void pollTimeoutFail() throws InterruptedException {
    assertNull(testQueue.poll(10, TimeUnit.MILLISECONDS));
  }
  
  @Test
  public void removeObjectTest() {
    TestDelayed item = new TestDelayed(0);
    testQueue.add(item);
    
    assertFalse(testQueue.remove(new Object()));
    assertFalse(testQueue.remove(new TestDelayed(-1)));
    
    assertTrue(testQueue.remove(item));
  }
  
  @Test
  public void removeAllTest() {
    List<TestDelayed> toRemoveList = new ArrayList<TestDelayed>(TEST_QTY);
    List<TestDelayed> comparisonList = new ArrayList<TestDelayed>(TEST_QTY);
    boolean flip = false;
    for (int i = 0; i < TEST_QTY; i++) {
      TestDelayed item = new TestDelayed(i);
      testQueue.add(item);
      comparisonList.add(item);
      if (flip) {
        toRemoveList.add(item);
        flip = false;
      } else {
        flip = true;
      }
    }
    
    testQueue.removeAll(toRemoveList);
    assertEquals(TEST_QTY - toRemoveList.size(), testQueue.size());
    Iterator<TestDelayed> it = toRemoveList.iterator();
    while (it.hasNext()) {
      assertFalse(testQueue.contains(it.next()));
    }
    
    comparisonList.removeAll(toRemoveList);  // do operation on comparison list
    assertTrue(testQueue.containsAll(comparisonList));  // verify nothing additional was removed
  }
  
  @Test
  public void removeTest() {
    for (int i = 0; i < TEST_QTY; i++) {
      TestDelayed item = new TestDelayed(i * -1);
      testQueue.add(item);
      assertEquals(item, testQueue.remove());
    }
  }
  
  @Test (expected = NoSuchElementException.class)
  public void removeFail() {
    testQueue.remove();
    
    fail("Exception should have been thrown");
  }
  
  @Test
  public void retainAllTest() {
    populatePositive(testQueue);
    
    assertTrue(testQueue.retainAll(new ArrayList<TestDelayed>(0)));
    
    assertEquals(0, testQueue.size());
    
    populatePositive(testQueue);
    
    assertFalse(testQueue.retainAll(testQueue));
    
    List<TestDelayed> toRetainList = new ArrayList<TestDelayed>(TEST_QTY / 2);
    for (int i = 0; i < TEST_QTY / 2; i++) {
      toRetainList.add(new TestDelayed(i));
    }
    
    assertTrue(testQueue.retainAll(toRetainList));
    
    assertEquals(TEST_QTY / 2, testQueue.size());
    
    synchronized (testQueue.getLock()) {
      assertTrue(toRetainList.containsAll(testQueue));
    }
  }
  
  @Test (expected = NoSuchElementException.class)
  public void iteratorNextFail() {
    populatePositive(testQueue);
    synchronized (testQueue.getLock()) {
      Iterator<TestDelayed> it = testQueue.iterator();
      for(int i = 0; i <= TEST_QTY; i++) {
        it.next();
      }
    }
    
    fail("Exception should have been thrown");
  }
   
   @Test (expected = IllegalStateException.class)
   public void iteratorRemoveFail() {
    boolean thrown = false;
    Iterator<TestDelayed> it = testQueue.iterator();
    try {
      it.remove();
    } catch (IllegalStateException e) {
      thrown = true;
    }
    
    assertTrue(thrown);
    populatePositive(testQueue);
    it = testQueue.iterator();
    for (int i = 0; i < testQueue.size() / 2; i++) {
      it.next();
    }
    
    it.remove();
    it.remove();
    
    fail("Exception should have been thrown");
   }
   
   @Test
   public void consumerIteratorPeekTest() throws InterruptedException {
     TestDelayed item = new TestDelayed(0);
     testQueue.add(item);
     
     synchronized (testQueue.getLock()) {
       ConsumerIterator<TestDelayed> it = testQueue.consumeIterator();
       assertTrue(it.peek() == item);
     }
   }
   
   @Test
   public void consumerIteratorRemoveTest() throws InterruptedException {
     TestDelayed item = new TestDelayed(0);
     testQueue.add(item);
     
     synchronized (testQueue.getLock()) {
       ConsumerIterator<TestDelayed> it = testQueue.consumeIterator();
       assertTrue(it.remove() == item);
     }
   }
   
   @Test (expected = NoSuchElementException.class)
   public void consumerIteratorRemoveFail() throws InterruptedException {
    synchronized (testQueue.getLock()) {
      populateNegative(testQueue);
      
      ConsumerIterator<TestDelayed> it = testQueue.consumeIterator();
      while (it.hasNext()) {
        it.remove();
      }
      
      it.remove();
      
      fail("Exception should have been thrown");
    }
  }
   
  @Test (expected = ConcurrentModificationException.class)
  public void consumerIteratorConcurrentModificationFail() throws InterruptedException {
    synchronized (testQueue.getLock()) {
      populateNegative(testQueue);
      
      ConsumerIterator<TestDelayed> it = testQueue.consumeIterator();
      assertTrue(it.hasNext());
      
      assertNotNull(testQueue.poll());
      
      it.remove();
      fail("Exception should have been thrown");
    }
  }
  
 @Test (expected = ConcurrentModificationException.class)
 public void consumerIteratorConcurrentModificationEmptyFail() throws InterruptedException {
   synchronized (testQueue.getLock()) {
     testQueue.add(new TestDelayed(-1));
     
     ConsumerIterator<TestDelayed> it = testQueue.consumeIterator();
     assertTrue(it.hasNext());
     
     assertNotNull(testQueue.poll());
     
     it.remove();
     fail("Exception should have been thrown");
   }
 }
   
  @Test
  public void remainingCapacityTest() {
    assertEquals(Integer.MAX_VALUE, testQueue.remainingCapacity());
  }
  
  @Test
  public void toObjectArrayTest() {
    assertArrayEquals(testQueue.toArray(), new TestDelayed[0]);
    
    TestDelayed[] compare = new TestDelayed[TEST_QTY];
    for (int i = 0; i < TEST_QTY; i++) {
      TestDelayed td = new TestDelayed(i);
      compare[i] = td;
      testQueue.add(td);
    }
    
    assertArrayEquals(testQueue.toArray(), compare);
  }
  
  @Test
  public void toArrayTest() {
    TestDelayed[] emptyArray = new TestDelayed[0];
    assertTrue(testQueue.toArray(emptyArray) == emptyArray);
    
    TestDelayed[] compare = new TestDelayed[TEST_QTY];
    for (int i = 0; i < TEST_QTY; i++) {
      TestDelayed td = new TestDelayed(i);
      compare[i] = td;
      testQueue.add(td);
    }
    
    TestDelayed[] resultArray = new TestDelayed[testQueue.size()];
    
    assertTrue(testQueue.toArray(resultArray) == resultArray);
    assertArrayEquals(resultArray, compare);
    assertArrayEquals(testQueue.toArray(emptyArray), compare);
  }
  
  @Test
  public void drainToTest() {
    List<TestDelayed> drainToList = new ArrayList<TestDelayed>(TEST_QTY * 2);
    populateNegative(testQueue); // should fully drain
    
    testQueue.drainTo(drainToList);
    
    assertEquals(0, testQueue.size());
    assertNull(testQueue.peek());
    assertEquals(TEST_QTY, drainToList.size());
    
    populatePositive(testQueue);
    testQueue.drainTo(drainToList); // should only drain the zero entry
    

    assertEquals(TEST_QTY - 1, testQueue.size());
    assertNull(testQueue.peek());
    assertEquals(TEST_QTY + 1, drainToList.size());
  }
  
  @Test
  public void drainToLimitTest() {
    List<TestDelayed> drainToList = new ArrayList<TestDelayed>(TEST_QTY);
    populateNegative(testQueue); // should fully drain
    
    testQueue.drainTo(drainToList, TEST_QTY / 2);
    
    assertEquals(TEST_QTY - (TEST_QTY / 2), testQueue.size());
    assertEquals(TEST_QTY / 2, drainToList.size());
    assertNotNull(testQueue.peek());
  }
  
  @Test
  public void drainToLimitZeroTest() {
    testQueue.add(new TestDelayed(-1));
    testQueue.add(new TestDelayed(-2));
    
    assertEquals(0, testQueue.drainTo(new ArrayList<TestDelayed>(0), 0));
  }
  
  private class RealTimeDelayed extends TestDelayed {
    private final long creationTime;

    protected RealTimeDelayed(long delayInMs) {
      super(delayInMs);
      
      creationTime = System.currentTimeMillis();
    }
    
    @Override
    public long getDelay(TimeUnit unit) {
      long elapsedTime = System.currentTimeMillis() - creationTime;
      return unit.convert(delayInMs - elapsedTime, 
                          TimeUnit.MILLISECONDS);
    }
  }
}

package org.threadly.concurrent.collections;

import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import org.threadly.util.Clock;
import org.threadly.util.ListUtils;

/**
 * <p>This queue is very similar to {@link java.util.concurrent.DelayQueue} but has one major
 * difference.  This queue is designed around the idea that items can change their delay.
 * Items enter the queue with Long.MAX_VALUE delay, and then will just call reposition 
 * once they know when their next execution time is.</p>
 * 
 * <p>In order to allow an item to be repositioned like this, the item must implement the 
 * {@link DynamicDelayedUpdater} interface.</p>
 * 
 * @author jent - Mike Jensen
 * @since 1.0.0
 * @param <T> Parameter to indicate what type of item is contained in the queue
 */
public class DynamicDelayQueue<T extends Delayed> implements Queue<T>, 
                                                             BlockingQueue<T> {
  // tuned for performance
  protected static final int QUEUE_FRONT_PADDING = 0;
  protected static final int QUEUE_REAR_PADDING = 2;
  
  protected final boolean randomAccessQueue;
  protected final Object queueLock;
  protected final ConcurrentArrayList<T> queue;

  /**
   * Constructs a new {@link DynamicDelayQueue} queue.
   */
  public DynamicDelayQueue() {
    this(new Object());
  }

  /**
   * Constructs a queue, providing the lock that will be called 
   * on with .wait().  Thus it allows you to synchronize around
   * the .take() and have the lock released while the thread blocks.
   * 
   * @param queueLock lock that is used internally
   */
  public DynamicDelayQueue(Object queueLock) {
    queue = new ConcurrentArrayList<T>(queueLock, 
                                       QUEUE_FRONT_PADDING, 
                                       QUEUE_REAR_PADDING);
    randomAccessQueue = true; // must change if switch from ConcurrentArrayList
    this.queueLock = queueLock;
  }
  
  @Override
  public String toString() {
    return queue.toString();
  }
  
  /**
   * Returns the lock} that will be called with .wait during take.  
   * And must be synchronized on while using the iterator.
   * 
   * @return lock synchronized on internally
   */
  public Object getLock() {
    return queueLock;
  }
  
  /**
   * Does a full sort on the queue, this is usually not optimal.
   * It is better to call reposition(T e), but this could be used if 
   * many items moved at the same time.
   */
  public void sortQueue() {
    synchronized (queueLock) {
      Collections.sort(queue);
      
      queueLock.notifyAll();
    }
  }

  @Override
  public void put(T e) {
    // there is no limit, just add
    add(e);
  }
  
  @Override
  public boolean add(T e) {
    if (e == null) {
      return false;
    }
    
    synchronized (queueLock) {
      int insertionIndex = ListUtils.getInsertionEndIndex(queue, e.getDelay(TimeUnit.MILLISECONDS), 
                                                          randomAccessQueue);
      
      queue.add(insertionIndex, e);
      
      queueLock.notify();
    }
    
    return true;
  }

  /**
   * Called to reposition an item in the queue which's delay wants to be updated
   * since original insertion (or was originally inserted as addLast()).
   * 
   * It is expected that this function will be called to reposition before the items 
   * delay time is updated in the .getDelay(TimeUnit) call.  Once the queue is ready 
   * for the item to update, it will call allowDelayUpdate on the provided updater.  This 
   * call to allowDelayUpdate will happen before the reposition call returns.
   * 
   * @param e item currently in the queue
   * @param newDelayInMillis delay time that e will be updated to after reposition
   * @param updater class to call into when queue is ready for item to update delay
   */
  public void reposition(T e, long newDelayInMillis, 
                         DynamicDelayedUpdater updater) {
    if (e == null) {
      return;
    }

    synchronized (queueLock) {
      int insertionIndex = ListUtils.getInsertionEndIndex(queue, newDelayInMillis, 
                                                          randomAccessQueue);
      
      /* provide the option to search backwards since the item 
       * will most likely be towards the back of the queue */
      queue.reposition(e, insertionIndex, true);
      
      updater.allowDelayUpdate();
      
      queueLock.notifyAll();
    }
  }

  /**
   * Adds an item to the end of the queue, used as an optimization from add(T e)
   * when it is known the item will be at the end of the queue.
   * 
   * @param e item to add to queue
   */
  public void addLast(T e) {
    if (e == null) {
      throw new NullPointerException();
    }
    
    queue.addLast(e);
  }

  @Override
  public T element() {
    T result = peek();
    if (result == null) {
      throw new NoSuchElementException();
    }
    
    return result;
  }

  @Override
  public boolean offer(T e) {
    return add(e);
  }

  @Override
  public boolean offer(T e, long timeout, TimeUnit unit) {
    // there is no blocking for offer, so just add
    return add(e);
  }

  @Override
  public T peek() {
    T next = queue.peek();
    
    if (next != null && next.getDelay(TimeUnit.MILLISECONDS) > 0) {
      next = null;
    }
    
    return next;
  }

  @Override
  public T poll() {
    T next = queue.peek();
    if (next != null && next.getDelay(TimeUnit.MILLISECONDS) <= 0) {
      // we likely can win, so lets try
      synchronized (queueLock) {
        if ((next = queue.peek()) != null && 
            next.getDelay(TimeUnit.MILLISECONDS) <= 0) {
          return queue.remove(0);
        } else {
          return null;
        }
      }
    } else {
      return null;
    }
  }

  @Override
  public T poll(long timeout, TimeUnit unit) throws InterruptedException {
    long startTime = Clock.accurateTimeMillis();
    long timeoutTimeInMs = unit.toMillis(timeout);
    long remainingTimeInMs = timeoutTimeInMs;
    synchronized (queueLock) {
      T next = null;
      while (next == null && remainingTimeInMs > 0) {
        if ((next = queue.peek()) != null && 
            next.getDelay(TimeUnit.MILLISECONDS) <= 0) {
          return queue.remove(0);
        } else {
          long waitTime;
          if (next == null) {
            waitTime = remainingTimeInMs;
          } else {
            waitTime = Math.min(next.getDelay(TimeUnit.MILLISECONDS), 
                                remainingTimeInMs);
          }
          queueLock.wait(waitTime);
          next = null;
        }
        remainingTimeInMs = timeoutTimeInMs - (Clock.accurateTimeMillis() - startTime);
      }
    }
    return null;
  }
  
  /**
   * Must have queueLock synchronized before calling this function!
   * 
   * @return a queue item which has a delay of <= 0
   * @throws InterruptedException thrown if thread interrupted waiting for queue item
   */
  protected T blockTillAvailable() throws InterruptedException {
    T next;
    long nextDelay = 0;
    while ((next = queue.peek()) == null || 
           (nextDelay = next.getDelay(TimeUnit.MILLISECONDS)) > 0) {
      if (next == null) {
        queueLock.wait();
      } else {
        queueLock.wait(nextDelay);
      }
    }
    
    return next;
  }

  @Override
  public T take() throws InterruptedException {
    T next;
    synchronized (queueLock) {
      next = blockTillAvailable();
      queue.remove(0);
    }

    return next;
  }

  @Override
  public T remove() {
    T result = poll();
    if (result == null) {
      throw new NoSuchElementException();
    }
    
    return result;
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    synchronized (queueLock) {
      Iterator<? extends T> it = c.iterator();
      boolean added = it.hasNext();
      while (it.hasNext()) {
        add(it.next());
      }
      
      return added;
    }
  }

  @Override
  public void clear() {
    queue.clear();
  }

  @Override
  public boolean contains(Object o) {
    return queue.contains(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return queue.containsAll(c);
  }

  @Override
  public boolean isEmpty() {
    return queue.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    if (! Thread.holdsLock(queueLock)) {
      throw new IllegalStateException("Must have lock in order to get iterator");
    }
    
    return queue.iterator();
  }

  /**
   * Returns an iterator that consumes the queue as it is progressed.
   * 
   * @return ConsumerIterator for queue
   * @throws InterruptedException Thrown when thread is interrupted
   */
  public ConsumerIterator<T> consumeIterator() throws InterruptedException {
    if (! Thread.holdsLock(queueLock)) {
      throw new IllegalStateException("Must have lock in order to get iterator");
    }
    
    blockTillAvailable();
    
    return new ConsumerIterator<T>() {
      private T next = null;
      
      @Override
      public boolean hasNext() {
        if (next == null) {
          next = DynamicDelayQueue.this.peek();
        }
        
        return next != null;
      }
      
      @Override
      public T peek() {
        if (next == null) {
          next = DynamicDelayQueue.this.peek();
        }
        
        return next;
      }

      @Override
      public T remove() {
        T result;
        if (next != null) {
          result = next;
          synchronized (queueLock) {
            if (queue.isEmpty()) {
              throw new ConcurrentModificationException();
            }
            
            T removed = queue.remove(0);
            if (removed != next) {
              queue.add(0, removed);
              throw new ConcurrentModificationException();
            }
          }
          next = null;
        } else {
          result = DynamicDelayQueue.this.remove();
        }
        
        return result;
      }
    };
  }

  @Override
  public boolean remove(Object o) {
    return queue.remove(o);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return queue.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return queue.retainAll(c);
  }

  @Override
  public int size() {
    return queue.size();
  }

  @Override
  public Object[] toArray() {
    return queue.toArray();
  }

  @Override
  public <E> E[] toArray(E[] a) {
    return queue.toArray(a);
  }

  @Override
  public int remainingCapacity() {
    return Integer.MAX_VALUE;
  }

  @Override
  public int drainTo(Collection<? super T> c) {
    return drainTo(c, Integer.MAX_VALUE);
  }

  @Override
  public int drainTo(Collection<? super T> c, int maxElements) {
    if (maxElements <= 0) {
      return 0;
    }
    
    int addedElements = 0;
    // synchronize once to avoid constant grabbing and releasing of the lock
    synchronized (queueLock) {
      while (addedElements < maxElements && peek() != null) {
        c.add(poll());
        addedElements++;
      }
    }
    
    return addedElements;
  }
}

package org.threadly.concurrent.future;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.threadly.concurrent.event.RunnableListenerHelper;
import org.threadly.util.Clock;

/**
 * <p>This class is designed to be a helper when returning a single 
 * result asynchronously.  This is particularly useful if this result 
 * is produced over multiple threads (and thus the scheduler returned 
 * future is not useful).</p>
 * 
 * @author jent - Mike Jensen
 * @since 1.2.0
 * @param <T> type of returned object
 */
public class SettableListenableFuture<T> extends AbstractNoncancelableListenableFuture<T>
                                         implements ListenableFuture<T>, FutureCallback<T> {
  protected final RunnableListenerHelper listenerHelper;
  protected final Object resultLock;
  private volatile boolean done;
  private T result;
  private Throwable failure;
  
  /**
   * Constructs a new {@link SettableListenableFuture}.  You can return this 
   * immediately and provide a result to the object later when it is ready.
   */
  public SettableListenableFuture() {
    this.listenerHelper = new RunnableListenerHelper(true);
    resultLock = new Object();
    done = false;
    result = null;
    failure = null;
  }

  @Override
  public void addListener(Runnable listener) {
    listenerHelper.addListener(listener);
  }

  @Override
  public void addListener(Runnable listener, Executor executor) {
    listenerHelper.addListener(listener, executor);
  }

  @Override
  public void addCallback(FutureCallback<? super T> callback) {
    addCallback(callback, null);
  }

  @Override
  public void addCallback(FutureCallback<? super T> callback, Executor executor) {
    FutureUtils.addCallback(this, callback, executor);
  }
  
  /**
   * This call defers to setResult.  It is implemented so that you can construct this, 
   * return it immediately, but only later provide this as a callback to another 
   * ListenableFuture implementation.  
   * 
   * This should never be invoked by the implementor, this should only be invoked by other 
   * ListenableFuture's.
   * 
   * If this is being used to chain together ListenableFuture's, setResult/setFailure should 
   * never be called manually (or an exception will occur).
   * 
   * @param result Result object to provide to the future to be returned from .get() call
   */
  @Override
  public void handleResult(T result) {
    setResult(result);
  }
  
  /**
   * This call defers to setResult.  It is implemented so that you can construct this, 
   * return it immediately, but only later provide this as a callback to another 
   * ListenableFuture implementation.  
   * 
   * This should never be invoked by the implementor, this should only be invoked by other 
   * ListenableFuture's.
   * 
   * If this is being used to chain together ListenableFuture's, setResult/setFailure should 
   * never be called manually (or an exception will occur).
   * 
   * @param t exception to be provided as the cause from the ExecutionException thrown from .get() call
   */
  @Override
  public void handleFailure(Throwable t) {
    setFailure(t);
  }
  
  /**
   * Call to indicate this future is done, and provide the given result.  It 
   * is expected that only this or setFailure are called, and only called 
   * once.
   * 
   * @param result result to provide for future.get() calls, can be null
   */
  public void setResult(T result) {
    synchronized (resultLock) {
      setDone();
      
      this.result = result;
      
      resultLock.notifyAll();
    }
    
    // call outside of lock
    listenerHelper.callListeners();
  }
  
  /**
   * Call to indicate this future is done, and provide the occurred failure.  It 
   * is expected that only this or setResult are called, and only called once.  
   * If the provided failure is null, a new exception will be created so that 
   * something is always provided in the ExecutionException on calls to 'get'.
   * 
   * @param failure Throwable that caused failure during computation.
   */
  public void setFailure(Throwable failure) {
    if (failure == null) {
      failure = new Exception();
    }
    synchronized (resultLock) {
      setDone();
      
      this.failure = failure;
      
      resultLock.notifyAll();
    }
    
    // call outside of lock
    listenerHelper.callListeners();
  }
  
  // should be synchronized on resultLock before calling
  private void setDone() {
    if (done) {
      throw new IllegalStateException("Already done");
    }
    
    done = true;
  }

  @Override
  public boolean isDone() {
    return done;
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    synchronized (resultLock) {
      while (! done) {
        resultLock.wait();
      }
      
      if (failure != null) {
        throw new ExecutionException(failure);
      } else {
        return result;
      }
    }
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, 
                                                   ExecutionException,
                                                   TimeoutException {
    long startTime = Clock.accurateTimeMillis();
    long timeoutInMs = unit.toMillis(timeout);
    synchronized (resultLock) {
      long remainingInMs;
      while (! done && 
             (remainingInMs = timeoutInMs - (Clock.accurateTimeMillis() - startTime)) > 0) {
        resultLock.wait(remainingInMs);
      }
      
      if (failure != null) {
        throw new ExecutionException(failure);
      } else if (done) {
        return result;
      } else {
        throw new TimeoutException();
      }
    }
  }
}

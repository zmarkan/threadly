package org.threadly.concurrent.future;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.threadly.concurrent.CallableContainerInterface;
import org.threadly.concurrent.RunnableContainerInterface;
import org.threadly.concurrent.event.RunnableListenerHelper;

/**
 * <p>This is a future which can be executed.  Allowing you to construct the future with 
 * the interior work, submit it to an executor, and then return this future.</p>
 * 
 * @author jent - Mike Jensen
 * @since 1.0.0
 * @param <T> type of future implementation
 */
public class ListenableFutureTask<T> extends FutureTask<T> 
                                     implements ListenableRunnableFuture<T>, 
                                                CallableContainerInterface<T>, 
                                                RunnableContainerInterface {
  protected final RunnableListenerHelper listenerHelper;
  protected final boolean recurring;
  protected final Runnable runnable;
  protected final Callable<T> callable;
  
  /**
   * Constructs a runnable future with a runnable work unit.
   * 
   * @param recurring boolean to indicate if this task can run multiple times, and thus must be reset after each run
   * @param task runnable to be run
   */
  public ListenableFutureTask(boolean recurring, Runnable task) {
    this(recurring, task, null);
  }
  
  /**
   * Constructs a runnable future with a runnable work unit.
   * 
   * @param recurring boolean to indicate if this task can run multiple times, and thus must be reset after each run
   * @param task runnable to be run
   * @param result result to be provide after run has completed
   */
  public ListenableFutureTask(boolean recurring, Runnable task, T result) {
    super(Executors.callable(task, result));

    this.listenerHelper = new RunnableListenerHelper(true);
    this.recurring = recurring;
    this.runnable = task;
    this.callable = null;
  }

  /**
   * Constructs a runnable future with a callable work unit.
   * 
   * @param recurring boolean to indicate if this task can run multiple times, and thus must be reset after each run
   * @param task callable to be run
   */
  public ListenableFutureTask(boolean recurring, Callable<T> task) {
    super(task);

    this.listenerHelper = new RunnableListenerHelper(true);
    this.recurring = recurring;
    this.runnable = null;
    this.callable = task;
  }
  
  @Override
  public void run() {
    if (recurring) {
      super.runAndReset();
    } else {
      super.run();
    }
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
   * Can not be overridden, please use addListener as an alternative.
   */
  @Override
  protected final void done() {
    listenerHelper.callListeners();
  }

  @Override
  public Runnable getContainedRunnable() {
    return runnable;
  }

  @Override
  public Callable<T> getContainedCallable() {
    return callable;
  }
}
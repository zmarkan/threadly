package org.threadly.concurrent;

import java.util.List;
import java.util.concurrent.Callable;

import org.threadly.concurrent.PriorityScheduler.OneTimeTaskWrapper;
import org.threadly.concurrent.PriorityScheduler.RecurringTaskWrapper;
import org.threadly.concurrent.future.ListenableFutureTask;
import org.threadly.concurrent.future.ListenableRunnableFuture;
import org.threadly.concurrent.future.ListenableScheduledFuture;
import org.threadly.concurrent.future.ScheduledFutureDelegate;

/**
 * <p>This is a wrapper for {@link PriorityScheduler} to be a drop in replacement for any 
 * {@link java.util.concurrent.ScheduledExecutorService} (AKA the 
 * {@link java.util.concurrent.ScheduledThreadPoolExecutor} 
 * interface). It does make some performance sacrifices to adhere to this interface, but those
 * are pretty minimal.  The largest compromise in here is easily scheduleAtFixedRate (which you should 
 * read the javadocs for if you need).</p>
 * 
 * @author jent - Mike Jensen
 * @since 2.2.0 (existed since 1.0.0 as PriorityScheduledExecutorServiceWrapper)
 */
public class PrioritySchedulerServiceWrapper extends AbstractExecutorServiceWrapper {
  private final PriorityScheduler scheduler;
  
  /**
   * Constructs a new wrapper to adhere to the 
   * {@link java.util.concurrent.ScheduledExecutorService} interface.
   * 
   * @param scheduler scheduler implementation to rely on
   */
  public PrioritySchedulerServiceWrapper(PriorityScheduler scheduler) {
    super(scheduler);
    
    this.scheduler = scheduler;
  }

  @Override
  public void shutdown() {
    scheduler.shutdown();
  }

  /**
   * This call will stop the processor as quick as possible.  Any 
   * tasks which are awaiting execution will be canceled and returned 
   * as a result to this call.
   * 
   * Unlike java.util.concurrent.ExecutorService implementation there 
   * is no attempt to stop any currently execution tasks.
   *
   * This method does not wait for actively executing tasks to
   * terminate.  Use {@link #awaitTermination awaitTermination} to
   * do that.
   *
   * @return list of tasks that never commenced execution
   */
  @Override
  public List<Runnable> shutdownNow() {
    return scheduler.shutdownNow();
  }

  @Override
  public boolean isTerminated() {
    return scheduler.isShutdown() && 
           scheduler.getCurrentPoolSize() == 0;
  }

  @Override
  protected ListenableScheduledFuture<?> schedule(Runnable command, long delayInMillis) {
    ListenableRunnableFuture<Object> taskFuture = new ListenableFutureTask<Object>(false, command);
    OneTimeTaskWrapper ottw = scheduler.new OneTimeTaskWrapper(taskFuture, 
                                                               scheduler.getDefaultPriority(), 
                                                               delayInMillis);
    scheduler.addToQueue(ottw);
    
    return new ScheduledFutureDelegate<Object>(taskFuture, ottw);
  }

  @Override
  protected <V> ListenableScheduledFuture<V> schedule(Callable<V> callable, long delayInMillis) {
    ListenableRunnableFuture<V> taskFuture = new ListenableFutureTask<V>(false, callable);
    OneTimeTaskWrapper ottw = scheduler.new OneTimeTaskWrapper(taskFuture, 
                                                               scheduler.getDefaultPriority(), 
                                                               delayInMillis);
    scheduler.addToQueue(ottw);
    
    return new ScheduledFutureDelegate<V>(taskFuture, ottw);
  }

  @Override
  protected ListenableScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                                long initialDelayInMs,
                                                                long delayInMs) {
    // wrap the task to ensure the correct behavior on exceptions
    command = new ThrowableHandlingRecurringRunnable(scheduler, command);
    
    ListenableRunnableFuture<Object> taskFuture = new ListenableFutureTask<Object>(true, command);
    RecurringTaskWrapper rtw = scheduler.new RecurringTaskWrapper(taskFuture, 
                                                                  scheduler.getDefaultPriority(), 
                                                                  initialDelayInMs, delayInMs);
    scheduler.addToQueue(rtw);
    
    return new ScheduledFutureDelegate<Object>(taskFuture, rtw);
  }
}

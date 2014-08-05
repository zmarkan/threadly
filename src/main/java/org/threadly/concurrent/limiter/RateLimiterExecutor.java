package org.threadly.concurrent.limiter;

import java.util.concurrent.Callable;

import org.threadly.concurrent.AbstractSubmitterExecutor;
import org.threadly.concurrent.SimpleSchedulerInterface;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.ListenableFutureTask;
import org.threadly.util.ArgumentVerifier;
import org.threadly.util.Clock;

/**
 * <p>Another way to limit executions on a scheduler.  Unlike the {@link ExecutorLimiter} 
 * this does not attempt to limit concurrency.  Instead it schedules tasks on a scheduler 
 * so that given permits are only used at a rate per second.  This can be used for limiting 
 * the rate of data that you want to put on hardware resource (in a non-blocking way).</p>
 * 
 * <p>It is important to note that if something is executed and it exceeds the rate, it will 
 * be future tasks which are delayed longer.</p>
 * 
 * <p>It is also important to note that it is the responsibility of the application to not 
 * be providing more tasks into this limiter than can be consumed at the rate.  Since this 
 * limiter will not block, if provided tasks too fast they could continue to be scheduled 
 * out further and further.  This should be used to flatten out possible bursts that could 
 * be used in the application, it is not designed to be a push back mechanism for the 
 * application.</p>
 * 
 * @author jent - Mike Jensen
 * @since 2.0.0
 */
public class RateLimiterExecutor extends AbstractSubmitterExecutor {
  protected final SimpleSchedulerInterface scheduler;
  protected final int permitsPerSecond;
  private final Object permitLock;
  private long lastScheduleTime;
  
  /**
   * Constructs a new {@link RateLimiterExecutor}.  Tasks will be scheduled on the 
   * provided scheduler, so it is assumed that the scheduler will have enough threads 
   * to handle the average permit amount per task, per second.
   * 
   * @param scheduler scheduler to schedule/execute tasks on
   * @param permitsPerSecond how many permits should be allowed per second
   */
  public RateLimiterExecutor(SimpleSchedulerInterface scheduler, 
                             int permitsPerSecond) {
    ArgumentVerifier.assertNotNull(scheduler, "scheduler");
    ArgumentVerifier.assertGreaterThanZero(permitsPerSecond, "permitsPerSecond");
    
    this.scheduler = scheduler;
    this.permitsPerSecond = permitsPerSecond;
    this.permitLock = new Object();
    this.lastScheduleTime = Clock.lastKnownTimeMillis();
  }
  
  /**
   * This call will check how far out we have already scheduled tasks to be run.  
   * Because it is the applications responsibility to not provide tasks too fast for 
   * the limiter to run them, this can give an idea of how backed up tasks provided 
   * through this limiter actually are.
   * 
   * @return minimum delay in milliseconds for the next task to be provided
   */
  public int getMinimumDelay() {
    synchronized (permitLock) {
      return (int)Math.max(0, lastScheduleTime - Clock.lastKnownTimeMillis());
    }
  }
  
  /**
   * In order to help assist with avoiding to schedule too much on the scheduler 
   * at any given time, this call returns a future that will block until the 
   * delay for the next task falls below the maximum delay provided into this 
   * call.  If you want to ensure that the next task will execute immediately, 
   * you should provide a zero to this function.  If more tasks are added to the 
   * limiter after this call, it will NOT impact when this future will unblock.  
   * So this future is assuming that nothing else is added to the limiter after 
   * requested.
   * 
   * @param maximumDelay maximum delay in milliseconds until returned Future should unblock
   * @return Future that will unblock .get() calls once delay has been reduced below the provided maximum
   */
  public ListenableFuture<?> getFutureTillDelay(int maximumDelay) {
    int currentMinimumDelay = getMinimumDelay();
    if (currentMinimumDelay <= maximumDelay) {
      return FutureUtils.immediateResultFuture(null);
    } else {
      ListenableFutureTask<?> lft = new ListenableFutureTask<Object>(false, new Runnable() {
        @Override
        public void run() {
          // nothing to execute
        }
      });
      
      scheduler.schedule(lft, currentMinimumDelay - maximumDelay);
      
      return lft;
    }
  }
  
  /**
   * Exact same as execute counter part, except you can specify how many permits this 
   * task will require/use (instead of defaulting to 1).  The task will be scheduled 
   * out as far as necessary to ensure it conforms to the set rate.
   * 
   * @param permits resource permits for this task
   * @param task Runnable to execute when ready
   */
  public void execute(int permits, Runnable task) {
    ArgumentVerifier.assertNotNull(task, "task");
    ArgumentVerifier.assertNotNegative(permits, "permits");
    
    doExecute(permits, task);
  }

  /**
   * Exact same as the submit counter part, except you can specify how many permits this 
   * task will require/use (instead of defaulting to 1).  The task will be scheduled 
   * out as far as necessary to ensure it conforms to the set rate.
   * 
   * @param permits resource permits for this task
   * @param task Runnable to execute when ready
   * @return Future that will indicate when the execution of this task has completed
   */
  public ListenableFuture<?> submit(int permits, Runnable task) {
    return submit(permits, task, null);
  }

  /**
   * Exact same as the submit counter part, except you can specify how many permits this 
   * task will require/use (instead of defaulting to 1).  The task will be scheduled 
   * out as far as necessary to ensure it conforms to the set rate.
   * 
   * @param <T> type of result returned from the future
   * @param permits resource permits for this task
   * @param task Runnable to execute when ready
   * @param result result to return from future when task completes
   * @return Future that will return provided result when the execution has completed
   */
  public <T> ListenableFuture<T> submit(int permits, Runnable task, T result) {
    ArgumentVerifier.assertNotNull(task, "task");
    ArgumentVerifier.assertNotNegative(permits, "permits");
    
    ListenableFutureTask<T> lft = new ListenableFutureTask<T>(false, task, result);
    
    doExecute(permits, lft);
    
    return lft;
  }

  /**
   * Exact same as the submit counter part, except you can specify how many permits this 
   * task will require/use (instead of defaulting to 1).  The task will be scheduled 
   * out as far as necessary to ensure it conforms to the set rate.
   * 
   * @param <T> type of result returned from the future
   * @param permits resource permits for this task
   * @param task Callable to execute when ready
   * @return Future that will return the callables provided result when the execution has completed
   */
  public <T> ListenableFuture<T> submit(int permits, Callable<T> task) {
    ArgumentVerifier.assertNotNull(task, "task");
    ArgumentVerifier.assertNotNegative(permits, "permits");
    
    ListenableFutureTask<T> lft = new ListenableFutureTask<T>(false, task);
    
    doExecute(permits, lft);
    
    return lft;
  }
  
  @Override
  protected void doExecute(Runnable task) {
    doExecute(1, task);
  }
  
  /**
   * Performs the execution by scheduling the task out as necessary.  The provided 
   * permits will impact the next execution's schedule time to ensure the given 
   * rate.
   * 
   * @param permits number of permits for this task
   * @param task Runnable to be executed once rate can be maintained
   */
  protected void doExecute(int permits, Runnable task) {
    synchronized (permitLock) {
      int effectiveDelay = (int)(((double)permits / permitsPerSecond) * 1000);
      long scheduleDelay = lastScheduleTime - Clock.accurateTimeMillis();
      if (scheduleDelay < 0) {
        scheduleDelay = 0;
      }
      
      scheduler.schedule(task, scheduleDelay);
      
      lastScheduleTime = Clock.lastKnownTimeMillis() + effectiveDelay + scheduleDelay;
    }
  }
}

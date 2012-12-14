/**
 *  Copyright (c) 2009-2012, Antony T Curtis, Xiphis OpenSource
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. Neither the name of Xiphis OpenSource nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 *  TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.xiphis.concurrent;

import com.xiphis.concurrent.internal.Governor;
import com.xiphis.concurrent.internal.Scheduler;
import com.xiphis.concurrent.internal.TBB;

/**
 * @author atcurtis
 */
public abstract class Task extends Scheduler.TaskBase
{

  /**
   *
   */
  protected Task()
  {
  }

  /**
   * Returns a root task.
   *
   * @param <T>
   * @param cls
   * @return
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public static <V, T extends Task> T allocateRoot(Class<T> cls, Object... arguments)
      throws InstantiationException,
             IllegalAccessException
  {
    Scheduler v;
    if (TBB.TASK_SCHEDULER_AUTO_INIT)
    {
      v = Governor.localSchedulerWithAutoInit();
    }
    else
    {
      v = Governor.localScheduler();
    }
    if (TBB.USE_ASSERT) assert v != null : "thread did not activate a task_scheduler_init object?";
    Task t = v.runningTask();
    // New root task becomes part of the currently running task's
    // cancellation context
    return v.allocateTask(cls, null, t.depth() + 1, t.context(), arguments);
  }

  /**
   * Returns a root task.
   *
   * @param <T>
   * @param cls
   * @return
   */
  public static <V, T extends Task> T allocateRoot(Factory<T> cls, Object... arguments)
  {
    Scheduler v;
    if (TBB.TASK_SCHEDULER_AUTO_INIT)
    {
      v = Governor.localSchedulerWithAutoInit();
    }
    else
    {
      v = Governor.localScheduler();
    }
    if (TBB.USE_ASSERT) assert v != null : "thread did not activate a task_scheduler_init object?";
    Task t = v.runningTask();
    // New root task becomes part of the currently running task's
    // cancellation context
    return v.allocateTask(cls, null, t.depth() + 1, t.context(), arguments);
  }

  ;

  // ------------------------------------------------------------------------
  // Allocating tasks
  // ------------------------------------------------------------------------

  /**
   * Returns a root task associated with user supplied context.
   *
   * @param <T>
   * @param cls
   * @param my_context
   * @return
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public static <T extends Task> T allocateRoot(TaskGroupContext my_context, Class<T> cls, Object... arguments)
      throws InstantiationException, IllegalAccessException
  {
    Scheduler v = Governor.localScheduler();
    if (TBB.USE_ASSERT) assert v != null : "thread did not activate a task_scheduler_init object?";
    Task current = v.runningTask();
    T t = v.allocateTask(cls, null, current.depth() + 1, my_context, arguments);
    // The supported usage model prohibits concurrent initial binding. Thus
    // we
    // do not need interlocked operations or fences here.
    if (my_context._kind == TaskGroupContext.binding_required)
    {
      if (TBB.USE_ASSERT) assert my_context._owner != null : "Context without owner";
      if (TBB.USE_ASSERT) assert my_context._parent == null : "Parent context set before initial binding";
      // If we are in the outermost task dispatch loop of a master thread,
      // then
      // there is nothing to bind this context to, and we skip the binding
      // part.
      if (current != v.dummyTask())
      {
        // By not using the fence here we get faster code in case of
        // normal execution
        // flow in exchange of a bit higher probability that in cases
        // when cancellation
        // is in flight we will take deeper traversal branch. Normally
        // cache coherency
        // mechanisms are efficient enough to deliver updated value most
        // of the time.
        int local_count_snapshot = my_context._owner.getLocalCancelCount();
        my_context._parent = current.context();
        int global_count_snapshot = Scheduler.getGlobalCancelCount();
        if (my_context._cancellationRequested.get() == 0)
        {
          if (local_count_snapshot == global_count_snapshot)
          {
            // It is possible that there is active cancellation
            // request in our
            // parents chain. Fortunately the equality of the local
            // and global
            // counters means that if this is the case it's already
            // been propagated
            // to our parent.
            my_context._cancellationRequested.set(current.context()._cancellationRequested.get());
          }
          else
          {
            // Another thread was propagating cancellation request
            // at the moment
            // when we set our parent, but since we do not use locks
            // we could've
            // been skipped. So we have to make sure that we get the
            // cancellation
            // request if one of our ancestors has been canceled.
            my_context.propagateCancellationFromAncestors();
          }
        }
      }
      my_context._kind = TaskGroupContext.binding_completed;
    }
    // else the context either has already been associated with its parent
    // or is isolated
    return t;
  }

  /**
   * Returns a root task associated with user supplied context.
   *
   * @param <T>
   * @param cls
   * @param my_context
   * @return
   */
  public static <T extends Task> T allocateRoot(TaskGroupContext my_context, Factory<T> cls, Object... arguments)
  {
    Scheduler v = Governor.localScheduler();
    if (TBB.USE_ASSERT) assert v != null : "thread did not activate a task_scheduler_init object?";
    Task current = v.runningTask();
    T t = v.allocateTask(cls, null, current.depth() + 1, my_context, arguments);
    // The supported usage model prohibits concurrent initial binding. Thus
    // we
    // do not need interlocked operations or fences here.
    if (my_context._kind == TaskGroupContext.binding_required)
    {
      if (TBB.USE_ASSERT) assert my_context._owner != null : "Context without owner";
      if (TBB.USE_ASSERT) assert my_context._parent == null : "Parent context set before initial binding";
      // If we are in the outermost task dispatch loop of a master thread,
      // then
      // there is nothing to bind this context to, and we skip the binding
      // part.
      if (current != v.dummyTask())
      {
        // By not using the fence here we get faster code in case of
        // normal execution
        // flow in exchange of a bit higher probability that in cases
        // when cancellation
        // is in flight we will take deeper traversal branch. Normally
        // cache coherency
        // mechanisms are efficient enough to deliver updated value most
        // of the time.
        int local_count_snapshot = my_context._owner.getLocalCancelCount();
        my_context._parent = current.context();
        int global_count_snapshot = Scheduler.getGlobalCancelCount();
        if (my_context._cancellationRequested.get() == 0)
        {
          if (local_count_snapshot == global_count_snapshot)
          {
            // It is possible that there is active cancellation
            // request in our
            // parents chain. Fortunately the equality of the local
            // and global
            // counters means that if this is the case it's already
            // been propagated
            // to our parent.
            my_context._cancellationRequested.set(current.context()._cancellationRequested.get());
          }
          else
          {
            // Another thread was propagating cancellation request
            // at the moment
            // when we set our parent, but since we do not use locks
            // we could've
            // been skipped. So we have to make sure that we get the
            // cancellation
            // request if one of our ancestors has been canceled.
            my_context.propagateCancellationFromAncestors();
          }
        }
      }
      my_context._kind = TaskGroupContext.binding_completed;
    }
    // else the context either has already been associated with its parent
    // or is isolated
    return t;
  }

  /**
   * Spawn task allocated by allocate_root, wait for it to complete, and
   * deallocate it.
   * <p/>
   * The thread that calls spawn_root_and_wait must be the same thread that
   * allocated the task.
   *
   * @param root
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public static void spawnRootAndWait(Task root)
  {
    if (TBB.USE_ASSERT) assert root.isOwnedByCurrentThread() : "'root' not owned by current thread";
    Scheduler.TaskBase.spawnRootAndWait(root);
  }

  /**
   * Spawn root tasks on list and wait for all of them to finish.
   * <p/>
   * If there are more tasks than worker threads, the tasks are spawned in
   * order of front to back.
   *
   * @param root_list
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public static void spawnRootAndWait(TaskList root_list)
  {
    Task t = root_list.first[0];
    if (t != null)
    {
      if (TBB.USE_ASSERT) assert t.isOwnedByCurrentThread() : "'this' not owned by current thread";
      t.owner().spawnRootAndWait(t, root_list.next_ptr);
      root_list.clear();
    }

  }

  /**
   * The innermost task being executed or destroyed by the current thread at
   * the moment.
   *
   * @return
   */
  public static Task currentTask()
  {
    Scheduler v;
    if (TBB.TASK_SCHEDULER_AUTO_INIT)
    {
      v = Governor.localSchedulerWithAutoInit();
    }
    else
    {
      v = Governor.localScheduler();
    }
    if (TBB.USE_ASSERT) assert v.assertOkay() && v.runningTask() != null;
    return v.runningTask();
  }

  /**
   * @return
   */
  public abstract Task execute()
      throws InstantiationException, IllegalAccessException;

  /**
   * Returns a continuation task of *this.
   * <p/>
   * The continuation's parent becomes the parent of this.
   *
   * @param <T>
   * @param cls
   * @return
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  @Override
  protected final <T extends Task> T allocateContinuation(Class<T> cls, Object... arguments)
      throws InstantiationException,
             IllegalAccessException
  {
    if (TBB.USE_ASSERT) TBB.AssertOkay(this);
    if (TBB.USE_ASSERT)
      assert TBB.RELAXED_OWNERSHIP || Governor.localScheduler() == owner() : "thread does not own this";
    return super.allocateContinuation(cls, arguments);
  }

  /**
   * Returns a continuation task of *this.
   * <p/>
   * The continuation's parent becomes the parent of this.
   *
   * @param <T>
   * @param cls
   * @return
   */
  @Override
  protected final <T extends Task> T allocateContinuation(Factory<T> cls, Object... arguments)
  {
    if (TBB.USE_ASSERT) TBB.AssertOkay(this);
    if (TBB.USE_ASSERT)
      assert TBB.RELAXED_OWNERSHIP || Governor.localScheduler() == owner() : "thread does not own this";
    return super.allocateContinuation(cls, arguments);
  }

  /**
   * Like allocateChild, except that task's parent becomes "t", not this.
   * <p/>
   * Typically used in conjunction with schedule_to_reexecute to implement
   * while loops.
   * <p/>
   * Atomically increments the reference count of t.parent()
   *
   * @param <T>
   * @param cls
   * @param parent
   * @return
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  @Override
  public final <T extends Task> T allocateAdditionalChildOf(Task parent, Class<T> cls, Object... arguments)
      throws InstantiationException, IllegalAccessException
  {
    if (TBB.USE_ASSERT) TBB.AssertOkay(this);
    return super.allocateAdditionalChildOf(parent, cls, arguments);
  }

  /**
   * Like allocateChild, except that task's parent becomes "t", not this.
   * <p/>
   * Typically used in conjunction with schedule_to_reexecute to implement
   * while loops.
   * <p/>
   * Atomically increments the reference count of t.parent()
   *
   * @param <T>
   * @param cls
   * @param parent
   * @return
   */
  @Override
  public final <T extends Task> T allocateAdditionalChildOf(Task parent, Factory<T> cls, Object... arguments)
  {
    if (TBB.USE_ASSERT) TBB.AssertOkay(this);
    return super.allocateAdditionalChildOf(parent, cls, arguments);
  }

  /**
   * Destroy a task
   * <p/>
   * Usually, calling this method is unnecessary, because a task is implicitly
   * deleted after its execute() method runs. However, sometimes a task needs
   * to be explicitly deallocated, such as when a root task is used as the
   * parent in spawnAndWaitForAll.
   *
   * @param victim
   */
  @Override
  public final void destroy(Task victim)
  {
    if (TBB.USE_ASSERT) assert isOwnedByCurrentThread() : "'this' not owned by current thread";
    if (TBB.USE_ASSERT) assert victim.ref_count() == (TBB.ConcurrentWaitsEnabled(
        victim) ? 1 : 0) : "Task being destroyed must not have children";
    if (TBB.USE_ASSERT) assert victim.state() == State.allocated : "illegal state for victim task";
    if (TBB.USE_ASSERT)
      assert victim.parent() == null || victim.parent().state() != State.allocated : "attempt to destroy child of running or corrupted parent?";
    super.destroy(victim);
  }

  // ------------------------------------------------------------------------
  // Recycling of tasks
  // ------------------------------------------------------------------------

  /**
   * Change this to be a continuation of its former self.
   * <p/>
   * The caller must guarantee that the task's refcount does not become zero
   * until after the method execute() returns. Typically, this is done by
   * having method execute() return a pointer to a child of the task. If the
   * guarantee cannot be made, use method recycleAsSafeContinuation instead.
   * <p/>
   * Because of the hazard, this method may be deprecated in the future.
   */
  @Override
  protected final void recycleAsContinuation()
  {
    if (TBB.USE_ASSERT) assert state() == State.executing : "execute not running?";
    super.recycleAsContinuation();
  }

  /**
   * Recommended to use, safe variant of recycleAsContinuation.
   * <p/>
   * For safety, it requires additional increment of _refCount.
   */
  @Override
  protected final void recycleAsSafeContinuation()
  {
    if (TBB.USE_ASSERT) assert state() == State.executing : "execute not running?";
    super.recycleAsSafeContinuation();
  }

  /**
   * Change this to be a child of new_parent.
   *
   * @param new_parent
   */
  @Override
  protected final void recycleAsChildOf(Task new_parent)
  {
    if (TBB.USE_ASSERT)
      assert state() == State.executing || state() == State.allocated : "execute not running, or already recycled";
    if (TBB.USE_ASSERT) assert ref_count() == 0 : "no child tasks allowed when recycled as a child";
    if (TBB.USE_ASSERT) assert parent() == null : "parent must be null";
    if (TBB.USE_ASSERT) assert new_parent.state() != State.freed : "parent already freed";
    super.recycleAsChildOf(new_parent);
  }

  /**
   * Schedule this for reexecution after current execute() returns.
   * <p/>
   * Requires that this.execute() be running.
   */
  @Override
  protected final void recycleToReexecute()
  {
    if (TBB.USE_ASSERT) assert state() == State.executing : "execute not running, or already recycled";
    if (TBB.USE_ASSERT) assert ref_count() == 0 : "no child tasks allowed when recycled for reexecution";
    super.recycleToReexecute();
  }

  /**
   * Set scheduling depth to given value.
   * <p/>
   * The depth must be non-negative.
   *
   * @param new_depth
   */
  @Override
  public final void setDepth(int new_depth)
  {
    if (TBB.USE_ASSERT) assert state() != State.ready : "cannot change depth of ready task";
    if (TBB.USE_ASSERT) assert new_depth >= 0 : "depth cannot be negative";
    super.setDepth(new_depth);
  }

  /**
   * Returns a child task of this.
   *
   * @param <T>
   * @param cls
   * @return
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public final <T extends Task> T allocateChild(Class<T> cls, Object... arguments)
      throws InstantiationException,
             IllegalAccessException
  {
    if (TBB.USE_ASSERT) TBB.AssertOkay(this);
    Scheduler s;
    if (TBB.RELAXED_OWNERSHIP)
    {
      s = Governor.localScheduler();
    }
    else
    {
      s = owner();
      if (TBB.USE_ASSERT) assert Governor.localScheduler() == s : "thread does not own this";
    }
    return s.allocateTask(cls, this, depth() + 1, context(), arguments);
  }

  // ------------------------------------------------------------------------
  // Spawning and blocking
  // ------------------------------------------------------------------------

  /**
   * Returns a child task of this.
   *
   * @param <T>
   * @param cls
   * @return
   */
  public final <T extends Task> T allocateChild(Factory<T> cls, Object... arguments)
  {
    if (TBB.USE_ASSERT) TBB.AssertOkay(this);
    Scheduler s;
    if (TBB.RELAXED_OWNERSHIP)
    {
      s = Governor.localScheduler();
    }
    else
    {
      s = owner();
      if (TBB.USE_ASSERT) assert Governor.localScheduler() == s : "thread does not own this";
    }
    return s.allocateTask(cls, this, depth() + 1, context(), arguments);
  }

  /**
   * Change scheduling depth by given amount.
   * <p/>
   * The resulting depth must be non-negative.
   *
   * @param delta
   */
  public final void addToDepth(int delta)
  {
    if (TBB.USE_ASSERT) assert state() != State.ready : "cannot change depth of ready task";
    int new_depth = depth() + delta;
    if (TBB.USE_ASSERT) assert new_depth >= 0 : "depth cannot be negative";
    super.setDepth(new_depth);
  }

  /**
   * Schedule task for execution when a worker becomes available.
   * <p/>
   * After all children spawned so far finish their method task::execute,
   * their parent's method task::execute may start running. Therefore, it is
   * important to ensure that at least one child has not completed until the
   * parent is ready to run.
   *
   * @param child
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public final void spawn(Task child)
  {
    if (TBB.USE_ASSERT) assert isOwnedByCurrentThread() : "'this' not owned by current thread";
    Scheduler.TaskBase.spawn(owner(), child);
  }

  /**
   * Spawn multiple tasks and clear list.
   * <p/>
   * All of the tasks must be at the same depth.
   *
   * @param list
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public final void spawn(TaskList list)
  {
    if (TBB.USE_ASSERT) assert isOwnedByCurrentThread() : "'this' not owned by current thread";
    Task t = list.first[0];
    if (t != null)
    {
      owner().spawn(t, list.next_ptr);
      list.clear();
    }
  }

  /**
   * Similar to spawn followed by waitForAll, but more efficient.
   *
   * @param child
   */
  public final void spawnAndWaitForAll(Task child)
  {
    if (TBB.USE_ASSERT) assert isOwnedByCurrentThread() : "'this' not owned by current thread";
    owner().waitForAll(this, child);
  }

  /**
   * Similar to spawn followed by waitForAll, but more efficient.
   *
   * @param list
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public final void spawnAndWaitForAll(TaskList list)
  {
    if (TBB.USE_ASSERT) assert isOwnedByCurrentThread() : "'this' not owned by current thread";
    Task first = list.first[0];
    Task[] next_ptr = list.next_ptr;
    list.clear();
    Scheduler.TaskBase.spawnAndWaitForAll(this, first, next_ptr);
  }

  /**
   * Wait for reference count to become one, and set reference count to zero.
   * <p/>
   * Works on tasks while waiting.
   */
  public final void waitForAll()
  {
    if (TBB.USE_ASSERT) assert isOwnedByCurrentThread() : "'this' not owned by current thread";
    owner().waitForAll(this, null);
  }

  /**
   * True if task is owned by different thread than thread that owns its
   * parent.
   *
   * @return
   */
  public final boolean isStolenTask()
  {
    return owner() != parent().owner();
  }

  /**
   * True if this task is owned by the calling thread; false otherwise.
   *
   * @return
   */
  public final boolean isOwnedByCurrentThread()
  {
    return TBB.RELAXED_OWNERSHIP || Governor.localScheduler() == owner();
  }

  // ------------------------------------------------------------------------
  // Debugging
  // ------------------------------------------------------------------------

  /**
   * Invoked by scheduler to notify task that it ran on unexpected thread.
   * <p/>
   * Invoked before method execute() runs, if task is stolen, or task has
   * affinity but will be executed on another thread.
   * <p/>
   * The default action does nothing.
   */
  public void noteAffinity(int my_affinity_id)
  {
    // do nothing
  }

  // ------------------------------------------------------------------------
  // Affinity
  // ------------------------------------------------------------------------

  /**
   * Initiates cancellation of all tasks in this cancellation group and its
   * subordinate groups.
   * <p/>
   *
   * @return false if cancellation has already been requested, true otherwise.
   */
  public final boolean cancelGroupExecution()
  {
    return context().cancelGroupExecution();
  }

  // ------------------------------------------------------------------------
  // Task cancellation
  // ------------------------------------------------------------------------

  /**
   * Returns true if the context received cancellation request.
   *
   * @return
   */
  public final boolean isCancelled()
  {
    return context().isGroupExecutionCancelled();
  }

  /**
   * Enumeration of task states that the scheduler considers.
   *
   * @author atcurtis
   */
  public static enum State
  {
    /**
     * task is running, and will be destroyed after method execute()
     * completes.
     */
    executing,

    /**
     * task to be rescheduled.
     */
    reexecute,

    /**
     * task is in ready pool, or is going to be put there, or was just taken
     * off.
     */
    ready,

    /**
     * task object is freshly allocated or recycled.
     */
    allocated,

    /**
     * task object is on free list, or is going to be put there, or was just
     * taken off.
     */
    freed,

    /**
     * task to be recycled as continuation
     */
    recycle
  }

}

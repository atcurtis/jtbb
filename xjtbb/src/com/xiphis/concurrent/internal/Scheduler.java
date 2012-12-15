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
package com.xiphis.concurrent.internal;

import com.xiphis.concurrent.EmptyTask;
import com.xiphis.concurrent.Factory;
import com.xiphis.concurrent.Statistics;
import com.xiphis.concurrent.Task;
import com.xiphis.concurrent.TaskException;
import com.xiphis.concurrent.TaskGroupContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Scheduler
{
  private static final Logger LOG = TBB.LOG;

  public static final Factory<EmptyTask> _emptyTaskFactory;
  // For nested dispatch loops in masters and any dispatch loops in workers
  protected static final int parents_work_done = 1;
  // For outermost dispatch loops in masters
  protected static final int all_work_done = Integer.MIN_VALUE / 2;
  // For termination dispatch loops in masters
  protected static final int all_local_work_done = all_work_done + 1;
  static final ListNode<Scheduler> _schedulerListHead;
  static final AtomicInteger _globalCancelCount;
  static final Statistics _theStatistics;
  /**
   * It is never used for its direct purpose, and is introduced solely for the
   * sake of avoiding one extra conditional branch in the end of wait_for_all
   * method.
   */
  private static final TaskGroupContext dummy_context = new TaskGroupContext(TaskGroupContext.isolated,
                                                                             TaskGroupContext.default_traits);
  public final Statistics _statistics;
  protected final MailInbox _inbox;
  final ListNode<TaskGroupContext> _contextListHead;
  final ListNode<Scheduler> _node;
  private final Factory<TaskProxy> _taskProxyFactory = new Factory<TaskProxy>()
  {
    @SuppressWarnings("unchecked")
    public TaskProxy construct(Object... arguments)
    {
      return new TaskProxy((AtomicStampedReference<Task>) arguments[0]);
    }
  };
  protected int _localCancelCount;
  protected int _refCount;
  protected Task _dummyTask;
  protected Task _currentRunningTask;
  protected int _deepest;
  protected int _affinityId;
  protected int _taskNodeCount;
  boolean _registered, _autoInitialized;
  // ! Last observer_proxy processed by this scheduler
  ObserverProxy _localLastObserverProxy;

  protected Scheduler()
  {
    _statistics = TBB.GATHER_STATISTICS ? new Statistics() : null;
    _contextListHead = new ListNode<TaskGroupContext>(null);
    _node = new ListNode<Scheduler>(this);
    _inbox = new MailInbox();
    _contextListHead._prev.lazySet(_contextListHead);
    _contextListHead._next.lazySet(_contextListHead);
    _refCount = 1;
    _dummyTask = allocateTask(_emptyTaskFactory, null, -1, null, null);
    _dummyTask.prefix()._refCount.set(2);
  }

  public static int getGlobalCancelCount()
  {
    return _globalCancelCount.get();
  }

  public static void setLocalCancelCount(Scheduler s, int count)
  {
    s._localCancelCount = count;
  }

  protected static boolean isProxy(Task t)
  {
    return t.prefix()._taskProxy;
  }

  protected static boolean tallyCompletionOfOnePredecessor(Task s)
  {
    TaskPrefix p = s.prefix();
    int k = p._refCount.getAndDecrement();
    if (TBB.USE_ASSERT) assert k > 0 : "completion of task caused parent's reference count to underflow";
    return k == 1;
  }

  public static Scheduler createMasterScheduler(int numberOfThreads)
      throws InstantiationException, IllegalAccessException
  {
    Scheduler s = Governor._initScheduler.createScheduler(null);
    Task t = s.dummyTask();
    s.setRunningTask(t);
    t.prefix()._refCount.set(1);
    Governor.signOn(s);

    // Context to be used by root tasks by default (if the user has not
    // specified one).
    t.prefix()._context = new TaskGroupContext(TaskGroupContext.isolated, TaskGroupContext.default_traits);

    ListNode<Scheduler> node = s._node;
    synchronized (_schedulerListHead)
    {
      node._next.lazySet(_schedulerListHead._next.get());
      node._prev.lazySet(_schedulerListHead);
      _schedulerListHead._next.get()._prev.lazySet(node);
      _schedulerListHead._next.lazySet(node);
    }
    // s->init_stack_info();
    // Sync up the local cancellation _state with the global one. No need for
    // fence here.
    s._localCancelCount = _globalCancelCount.get();
    // __TBB_ASSERT( &task::self()==&t, NULL );

    s.setWorkerCount(numberOfThreads);

    // Process any existing observers.
    s.notifyEntryObservers();

    return s;
  }

  protected static Scheduler createWorkerScheduler(int l, Object... args)
      throws InstantiationException, IllegalAccessException
  {
    Scheduler s = Governor._initScheduler.createScheduler(args);
    // Task t = s.dummyTask();
    // s.setRunningTask(t);
    // Governor.signOn(s);

    // Put myself into the arena
    // #if !__TBB_TASK_DEQUE
    // ArenaSlot& slot = a.slot[index];
    // __TBB_ASSERT( slot.steal_end==-3,
    // "slot not allocated as locked worker?" );
    // s->arena_slot = &slot;
    // #endif /* !__TBB_TASK_DEQUE */
    s.dummyTask().prefix()._context = dummy_context;
    // Sync up the local cancellation _state with the global one. No need for
    // fence here.
    s._localCancelCount = _globalCancelCount.get();
    s.attachMailbox(l + 1);
    s.configureWorker(l);
    // #if __TBB_TASK_DEQUE
    // s->arena_index = index;
    // s->init_stack_info();
    // #else /* !__TBB_TASK_DEQUE */
    // TaskPool* t = s->dummy_slot.task_pool;
    // t->prefix().arena_index = index;
    // ITT_NOTIFY(sync_releasing, &slot);
    // slot.task_pool = t;
    // slot.steal_end = -2;
    // slot.owner_waits = false;
    // #endif /* !__TBB_TASK_DEQUE */

    return s;
  }

  public static void cleanupWorker(Scheduler s)
  {
    LOG.entering(s.getClass().getName(), "cleanupWorker");
    // GenericScheduler& s = *(GenericScheduler*)arg;
    // __TBB_ASSERT( s.dummy_slot.task_pool, "cleaning up worker with missing task pool" );
    s.notifyExitObservers(/* is_worker= */true);
    s.freeScheduler();
    // #if !__TBB_RML
    // a->remove_gc_reference();
    // #endif /* !__TBB_RML */
  }

  protected static void setRefCount(TaskPrefix tp, int i)
  {
    tp._refCount.set(i);
  }

  protected static Task[] getNextPtr(TaskPrefix tp)
  {
    return tp._next;
  }

  protected static void setOwner(TaskPrefix tp, Scheduler owner)
  {
    tp._owner = owner;
  }

  protected static void setState(TaskPrefix tp, Task.State state)
  {
    tp._state = state;
  }

  protected static void setRefCountActive(TaskPrefix tp, boolean active)
  {
    tp._refCountActive = active;
  }

  public final int getLocalCancelCount()
  {
    return _localCancelCount;
  }

  public final boolean isRegistered()
  {
    return _registered;
  }

  public final boolean wasAutoInitialized()
  {
    return _autoInitialized;
  }

  public final int getTaskNodeCount()
  {
    return _taskNodeCount;
  }

  public final int affinity()
  {
    return _affinityId;
  }

  protected void setRunningTask(Task t)
  {
    _currentRunningTask = t;
  }

  public final Task runningTask()
  {
    return _currentRunningTask;
  }

  public final Task dummyTask()
  {
    return _dummyTask;
  }

  final void localSpawnRootAndWait(Task first, Task[] next)
  {
    if (TBB.USE_ASSERT) assert Governor.localScheduler() == this;
    internalSpawnRootAndWait(first, next);
  }

  public final void spawnRootAndWait(Task first, Task[] next)
  {
    if (TBB.RELAXED_OWNERSHIP)
    {
      Governor.localScheduler().localSpawnRootAndWait(first, next);
    }
    else
    {
      internalSpawnRootAndWait(first, next);
    }
  }

  private void internalSpawnRootAndWait(Task first, Task[] next)
  {
    if (TBB.USE_ASSERT) assert first != null;
    AutoEmptyTask dummy = new AutoEmptyTask(this, first.prefix()._depth - 1, first.prefix()._context);                /* synchronized (dummy) */
    {
      int n = 0;
      for (Task t = first; ; t = t.prefix()._next[0])
      {
        ++n;
        if (TBB.USE_ASSERT) assert t.isOwnedByCurrentThread() : "root task not owned by current thread";
        if (TBB.USE_ASSERT) assert t.parent() == null : "not a root task, or already running";
        t.prefix()._parent = dummy.task();
        if (t.prefix()._next == next)
        {
          break;
        }
        if (TBB.USE_ASSERT)
          assert t.prefix()._context == t.prefix()._next[0].prefix()._context : "all the root tasks in list must share the same context";
      }
      dummy.prefix()._refCount.set(n + 1);
      if (n > 1)
      {
        spawn(first.prefix()._next[0], next);
      }
      LOG.log(Level.FINEST, "spawn_root_and_wait({0},{1}) calling {2}.loop", new Object[]{first, next, this});
      waitForAll(dummy.task(), first);
      LOG.log(Level.FINEST, "spawn_root_and_wait({0},{1})", new Object[]{first, next});
    }
  }

  final void localSpawn(Task first, Task[] next)
  {
    if (TBB.USE_ASSERT) assert Governor.localScheduler() == this;
    if (TBB.GATHER_STATISTICS)
    {
      timedSpawn(first, next);
    }
    else
    {
      schedulerSpawn(first, next);
    }
  }

  public final void spawn(Task first, Task[] next)
  {
    if (TBB.RELAXED_OWNERSHIP)
    {
      Governor.localScheduler().localSpawn(first, next);
    }
    else if (TBB.GATHER_STATISTICS)
    {
      timedSpawn(first, next);
    }
    else
    {
      schedulerSpawn(first, next);
    }
  }

  // Constants all_work_done and all_local_work_done are actually unreacheable
  // refcount values that prevent early quitting the dispatch loop. They are
  // defined to be in the middle of the range of negative values representable
  // by the reference_count type.

  private void timedSpawn(Task first, Task[] next)
  {
    long overhead = -System.nanoTime();
    schedulerSpawn(first, next);
    overhead -= System.nanoTime();
    _statistics.spawn_overhead += overhead;
  }

  protected abstract void schedulerSpawn(Task first, Task[] next);

  final void localWaitForAll(Task parent, Task child)
  {
    if (TBB.USE_ASSERT) assert Governor.localScheduler() == this;
    if (child != null)
    {
      child.prefix()._owner = this;
    }
    if (TBB.GATHER_STATISTICS)
    {
      timedWaitForAll(parent, child);
    }
    else
    {
      internalWaitForAll(parent, child, null);
    }
  }

  public final void waitForAll(Task parent, Task child)
  {
    if (TBB.RELAXED_OWNERSHIP)
    {
      Governor.localScheduler().localWaitForAll(parent, child);
    }
    else
    {
      if (TBB.USE_ASSERT)
        assert child == null || child.prefix()._owner == this : "task child is not owned by the current thread";
      if (TBB.USE_ASSERT)
        assert child == null || child.parent() == null || child.parent() == parent : "child has a wrong parent";
      if (TBB.GATHER_STATISTICS)
      {
        timedWaitForAll(parent, child);
      }
      else
      {
        internalWaitForAll(parent, child, null);
      }
    }
  }

  protected abstract Task getTask(int depth);

  protected boolean isWorker()
  {
    return false;
  }

  protected final Task getMailboxTask()
  {
    if (TBB.USE_ASSERT) assert _affinityId > 0 : "not in arena";
    int[] tag = new int[1];
    Task result = null;
    for (TaskProxy t = _inbox.pop(); t != null; t = _inbox.pop())
    {
      Task task = t._taskAndTag.get(tag);
      // __TBB_ASSERT( tat==task_proxy::mailbox_bit ||
      // tat==(tat|3)&&tat!=3, NULL );
      if (tag[0] != TaskProxy.mailbox_bit && t._taskAndTag.compareAndSet(task, null, tag[0], TaskProxy.pool_bit))
      {
        // Successfully grabbed the task, and left pool seeker with job
        // of freeing the proxy.
        // ITT_NOTIFY( sync_acquired, _inbox.outbox() );
        result = task;
        result.prefix()._owner = this;
        break;
      }
      freeTaskProxy(t);
    }
    return result;
  }

  protected final TaskProxy allocateTaskProxy(Task t, MailOutbox outbox)
  {
    Object[] args = {new AtomicStampedReference<Task>(t, 3)};
    TaskProxy proxy = allocateTask(_taskProxyFactory, null, t.depth(), null, args);
    // Mark as a proxy
    proxy.prefix()._taskProxy = true;
    proxy._outbox = outbox;
    // proxy._taskAndTag.set(t, 3);
    proxy._nextInMailbox = null;
    // ITT_NOTIFY( sync_releasing, proxy._outbox );
    // Mail the proxy - after this point t may be destroyed by another
    // thread at any moment.
    proxy._outbox.push(proxy);
    return proxy;
  }

  protected final Task stripProxy(Task t)
  {
    if (TBB.USE_ASSERT) assert t.prefix()._taskProxy;
    TaskProxy tp = TaskProxy.class.cast(t);
    int[] tag = new int[1];
    Task task = tp._taskAndTag.get(tag);
    if ((tag[0] & 3) == 3)
    {
      // proxy is shared by a pool and a mailbox.
      // Attempt to transition it to "empty proxy in mailbox" _state.
      if (tp._taskAndTag.compareAndSet(task, null, tag[0], TaskProxy.mailbox_bit))
      {
        // Successfully grabbed the task, and left the mailbox with the
        // job of freeing the proxy.
        return task;
      }
      //if (TBB.USE_ASSERT) assert tp._taskAndTag.getStamp() == TaskProxy.pool_bit;
    }
    else
    {
      // We have exclusive access to the proxy
      // __TBB_ASSERT( (tat&3)==task_proxy::pool_bit,
      // "task did not come from pool?" );
      // __TBB_ASSERT ( !(tat&~3),
      // "Empty proxy in the pool contains non-zero task pointer" );
    }
    tp.prefix()._state = Task.State.allocated;
    freeTaskProxy(tp);
    // Another thread grabbed the underlying task via their mailbox
    return null;
  }

  protected final void freeTaskProxy(TaskProxy tp)
  {
    tp._outbox = null;
    tp._nextInMailbox = null;
    tp._taskAndTag.set(null, 0xDEADBEEF);
    freeTask(tp);
  }

  protected boolean canSteal()
  {
    return false;
  }

  protected Task stealTask(int depth)
  {
    return null;
  }

  protected int getPoolSize()
  {
    return 1;
  }

  protected boolean waitWhilePoolIsEmpty()
  {
    return false;
  }

  private void timedWaitForAll(Task parent, Task child)
  {
    long[] overhead = {-System.nanoTime()};
    try
    {
      internalWaitForAll(parent, child, overhead);
    }
    finally
    {
      overhead[0] += System.nanoTime();
      _statistics.wait_for_all_overhead += overhead[0];
    }
  }

  private void internalWaitForAll(Task parent, Task child, long[] overhead)
  {
    if (TBB.USE_ASSERT)
      assert parent.ref_count() >= (child != null && child.parent() == parent ? 2 : 1) : "ref_count is too small";
    if (TBB.USE_ASSERT)
      assert parent.prefix()._context != null || (isWorker() && parent == dummyTask()) : "parent task does not have context";
    if (TBB.USE_ASSERT) assert assertOkay();

    Task t = child;
    int quit_point, d;

    if (runningTask() == dummyTask())
    {
      // We are in the outermost task dispatch loop of a master
      // thread,
      if (TBB.USE_ASSERT) assert !isWorker();
      quit_point = parent == dummyTask() ? all_local_work_done : all_work_done;
      // Forcefully make this loop operate at zero depth.
      d = 0;
    }
    else
    {
      quit_point = parents_work_done;
      d = parent.prefix()._depth + 1;
    }

    Task old_innermost_running_task = runningTask();
    loop:
    for (; ; )
    {
      // Outer loop steals tasks when necessary.
      try
      {
        for (; ; )
        {
          // Middle loop evaluates tasks that are pulled off "array".
          do
          {
            // Inner loop evaluates tasks that are handed directly
            // to us by other tasks.
            while (t != null)
            {
              if (TBB.USE_ASSERT) assert !isProxy(t) : "unexpected proxy";
              if (TBB.USE_ASSERT) assert t.prefix()._owner == this;
              if (TBB.USE_ASSERT) assert !((t.prefix()._context._cancellationRequested.get() == 0)
                  && !(t.state() == Task.State.allocated || t.state() == Task.State.ready || t.state() == Task.State.reexecute));
              if (TBB.USE_ASSERT) assert assertOkay();

              Task t_next = null;
              setRunningTask(t);
              t.prefix()._state = Task.State.executing;

              if (t.prefix()._context._cancellationRequested.get() == 0)
              {
                LOG.log(Level.FINEST, "{0}.wait_for_all: {1}.execute", new Object[]{this, t});

                if (TBB.GATHER_STATISTICS)
                {
                  try
                  {
                    ++_statistics.execute_count;
                    overhead[0] += System.nanoTime();
                    t_next = t.execute();
                  }
                  finally
                  {
                    overhead[0] -= System.nanoTime();

                    if (t_next != null)
                    {
                      int next_affinity = t_next.prefix()._affinity;
                      if (next_affinity != 0 && next_affinity != _affinityId)
                      {
                        ++_statistics.proxy_bypass_count;
                      }
                    }
                  }
                }
                else
                {
                  t_next = t.execute();
                }

              }

              if (t_next != null)
              {
                if (TBB.USE_ASSERT)
                  assert t_next.state() == Task.State.allocated : "if task::execute() returns task, it must be marked as allocated";
                // The store here has a subtle secondary effect
                // - it fetches *t_next into cache.
                t_next.prefix()._owner = this;
              }
              if (TBB.USE_ASSERT) assert assertOkay();
              switch (t.prefix()._state)
              {
              case executing:
              {
                // this block was copied below to case
                // task::recycle
                // when making changes, check it too
                Task s = t.parent();
                if (TBB.USE_ASSERT) assert runningTask() == t;
                if (TBB.USE_ASSERT)
                  assert t.prefix()._refCount.get() == 0 : "Task still has children after it has been executed";
                // t->~task();
                if (s != null)
                {
                  if (tallyCompletionOfOnePredecessor(s))
                  {
                    int s_depth = s.depth();
                    s.prefix()._refCountActive = false;
                    s.prefix()._owner = this;
                    if (t_next == null && (!TBB.SPAWN_OPTIMIZE || s_depth >= _deepest && s_depth >= d))
                    {
                      // Eliminate spawn/get_task pair.
                      // The elimination is valid because
                      // the spawn would set
                      // _deepest==s_depth,
                      // and the subsequent call to
                      // get_task(d) would grab task s and
                      // restore _deepest to its former
                      // value.
                      t_next = s;
                    }
                    else
                    {
                      spawn(s, s.prefix()._next);
                      if (TBB.USE_ASSERT) assert assertOkay();
                    }
                  }
                }
                freeTask(t);
                break;
              }

              case recycle:
              { // _state set by
                // recycle_as_safe_continuation()
                t.prefix()._state = Task.State.allocated;
                // for safe continuation, need atomically
                // decrement _refCount;
                // the block was copied from above case
                // task::executing, and changed.
                // Use "s" here as name for t, so that code
                // resembles case task::executing more closely.
                final Task s = t;
                if (tallyCompletionOfOnePredecessor(s))
                {
                  // Unused load is put here for sake of
                  // inserting an "acquire" fence.
                  s.depth();
                  s.prefix()._refCountActive = false;
                  if (TBB.USE_ASSERT) assert s.prefix()._owner == this : "ownership corrupt?";
                  if (TBB.USE_ASSERT) assert s.prefix()._depth >= d;
                  if (t_next == null)
                  {
                    t_next = s;
                  }
                  else
                  {
                    spawn(s, s.prefix()._next);
                    if (TBB.USE_ASSERT) assert assertOkay();
                  }
                }
                break;
              }

              case reexecute: // set by recycle_to_reexecute()
                if (TBB.USE_ASSERT) assert t_next != null && t_next != t : "reexecution requires that method 'execute' return another task";
                // TBB_TRACE(("%p.wait_for_all: put task %p back into array",this,t));
                t.prefix()._state = Task.State.allocated;
                spawn(t, t.prefix()._next);
                if (TBB.USE_ASSERT) assert assertOkay();
                break;
              case allocated:
                break;
              case ready:
              {
                IllegalStateException e =
                    new IllegalStateException("task is in READY state upon return from method execute()");
                LOG.throwing(Scheduler.class.getName(), "internalWaitForAll", e);
                throw e;
              }
              default:
                IllegalStateException e = new IllegalStateException("illegal state");
                LOG.throwing(Scheduler.class.getName(), "internalWaitForAll", e);
                throw e;
              }
              // __TBB_ASSERT( !t_next||t_next->prefix()._depth>=d,
              // NULL );
              t = t_next;
            } // end of scheduler bypass loop
            if (TBB.USE_ASSERT) assert assertOkay();

            // If the _parent's descendants are finished with and we
            // are not in
            // the outermost dispatch loop of a master thread, then
            // we are done.
            // This is necessary to prevent unbounded stack growth
            // in case of deep
            // wait_for_all nesting.
            // Note that we cannot return from master's outermost
            // dispatch loop
            // until we process all the tasks in the local pool,
            // since in case
            // of multiple masters this could have left some of them
            // forever
            // waiting for their stolen children to be processed.
            if (parent.prefix()._refCount.get() == quit_point)
            {
              break;
            }
            t = getTask(d);
            if (TBB.USE_ASSERT) assert t == null || !isProxy(t) : "unexpected proxy";
            if (TBB.USE_ASSERT) assert assertOkay();
            if (TBB.USE_ASSERT && t != null)
            {
              TBB.AssertOkay(t);
              assert t.prefix()._owner == this : "thread got task that it does not own";
            }
          } while (t != null); // end of local task array processing
          // loop

          if (quit_point == all_local_work_done)
          {
            // __TBB_ASSERT( arena_slot == &dummy_slot &&
            // arena_slot->head == 0 && arena_slot->tail == 0, NULL
            // );
            setRunningTask(old_innermost_running_task);
            return;
          }

          _inbox.setIsIdle(true);
          // __TBB_ASSERT(
          // arena->prefix().number_of_workers>0||_parent.prefix()._refCount==1,
          // "deadlock detected" );
          // The _state "failure_count==-1" is used only when
          // itt_possible is true,
          // and denotes that a sync_prepare has not yet been issued.
          for (int failure_count = 0; ; ++failure_count)
          {
            if (parent.prefix()._refCount.get() == 1)
            {
              _inbox.setIsIdle(false);
              break loop;
            }
            // Try to steal a task from a random victim.
            int n = getPoolSize();
            if (n > 1)
            {
              if (_affinityId == 0 || (t = getMailboxTask()) == null)
              {
                if (canSteal())
                {
                  t = stealTask(d);
                }
                if (t != null && isProxy(t))
                {
                  t = stripProxy(t);
                  if (TBB.GATHER_STATISTICS && t != null)
                  {
                    ++_statistics.proxy_steal_count;
                  }
                }
                if (t != null)
                {
                  if (TBB.GATHER_STATISTICS)
                  {
                    ++_statistics.steal_count;
                  }
                  setRunningTask(t);
                  t.noteAffinity(_affinityId);
                }
              }
              else if (TBB.GATHER_STATISTICS)
              {
                ++_statistics.mail_received_count;
              }
              if (t != null)
              {
                // __TBB_ASSERT(t,NULL);
                // No memory fence required for read of
                // global_last_observer_proxy, because prior
                // fence on steal/mailbox suffices.
                if (_localLastObserverProxy != ObserverProxy.global_last_observer_proxy)
                {
                  notifyEntryObservers();
                }
                if (TBB.USE_ASSERT) assert t.prefix()._depth>=d;
                _inbox.setIsIdle(false);
                break;
              }
            }
            // Pause, even if we are going to yield, because the
            // yield might return immediately.
            //TBB.Pause(TBB.PAUSE_TIME);
            int yield_threshold = 2 * n;
            if (failure_count >= yield_threshold)
            {
              TBB.Yield();
              if (failure_count >= yield_threshold + 100)
              {
                boolean call_wait = (old_innermost_running_task == null) || (d == 0 && isWorker());
                if (call_wait && waitWhilePoolIsEmpty())
                {
                  failure_count = 0;
                }
                else
                {
                  failure_count = yield_threshold;
                }
              }
            }
          }
          if (t == null)
          {
            throw new IllegalStateException();
          }
          if (TBB.USE_ASSERT) assert !isProxy(t) : "unexpected proxy";
          t.prefix()._owner = this;
        } // end of stealing loop
      }
      catch (TaskException ex)
      {
        if (t.prefix()._context.cancelGroupExecution())
        {                                        /*
                                         * We are the first to signal cancellation, so store the
					 * exception that caused it.
					 */
          t.prefix()._context._exception = ex;
        }
      }
      catch (Throwable th)
      {
        th.printStackTrace();
        if (t == null)
        {
          TBB.rethrow(th);
        }
        else if (t.prefix()._context.cancelGroupExecution())
        {
					/*
					 * We are the first to signal cancellation, so store the
					 * exception that caused it.
					 */
          t.prefix()._context._exception = new TaskException(t.prefix()._context, th);
        }
      }

      if (t.prefix()._state == Task.State.recycle)
      { // _state set by
        // recycle_as_safe_continuation()
        t.prefix()._state = Task.State.allocated;
        // for safe continuation, need to atomically decrement
        // _refCount;
        if (t.prefix()._refCount.getAndDecrement() != 1)
        {
          t = null;
        }
      }
    }
    if (!TBB.ConcurrentWaitsEnabled(parent))
    {
      parent.prefix()._refCount.set(0);
    }
    parent.prefix()._refCountActive = false;
    setRunningTask(old_innermost_running_task);
    // if( _deepest<0 && innermost_running_task==_dummyTask && in_arena()
    // ) {
    // leave_arena(/*compress=*/true);
    // }
    if (TBB.USE_ASSERT) assert parent.prefix()._context != null && dummyTask().prefix()._context != null;
    TaskGroupContext parent_ctx = parent.prefix()._context;
    if (parent_ctx._cancellationRequested.get() != 0)
    {
      Throwable pe = parent_ctx._exception;
      if (runningTask() == dummyTask() && parent_ctx == dummyTask().prefix()._context)
      {
        // We are in the outermost task dispatch loop of a master
        // thread, and
        // the whole task tree has been collapsed. So we may clear
        // cancellation data.
        parent_ctx._cancellationRequested.set(0);
        parent_ctx._exception = null;
        if (TBB.USE_ASSERT) assert dummyTask().prefix()._context == parent_ctx || !TBB.CancellationInfoPresent(
            dummyTask()) : "Unexpected exception or cancellation data in the dummy task";
        // If possible, add assertion that master's dummy task
        // _context does not have children
        TBB.rethrow(pe);
      }
    }
    if (TBB.USE_ASSERT) assert !isWorker() || !TBB.CancellationInfoPresent(dummyTask()) : "Worker's dummy task context modified";
    if (TBB.USE_ASSERT) assert runningTask() != dummyTask() || !TBB.CancellationInfoPresent(dummyTask()) : "Unexpected exception or cancellation data in the master's dummy task";
    if (TBB.USE_ASSERT) assert assertOkay();
  }

  public abstract boolean assertOkay();

  @SuppressWarnings("unchecked")
  private <T extends Task> T instantiate(Class<T> cls, Object[] arguments)
      throws InstantiationException, IllegalAccessException
  {
    for (Constructor<?> c : cls.getConstructors())
    {
      if (!c.isVarArgs() && c.getParameterTypes().length != arguments.length)
      {
        continue;
      }
      try
      {
        if (TBB.GATHER_STATISTICS)
        {
          ++_statistics.reflect_construct_count;
        }
        return ((Constructor<T>) c).newInstance(arguments);
      }
      catch (IllegalArgumentException e)
      {
        continue;
      }
      catch (InvocationTargetException e)
      {
        TBB.rethrow(e.getCause());
      }
    }
    throw new IllegalArgumentException();
  }

  // int num_workers = arena.prefix().number_of_workers;
  // for ( int i = 0; i < num_workers; ++i ) {
  // // No fence is necessary here since the _context list of worker's
  // scheduler
  // // can contain anything of interest only after the first stealing was
  // done
  // // by that worker. And doing it applies the necessary fence
  // Scheduler s = arena.prefix()._workerList[i].scheduler;
  // // If the worker is in the middle of its startup sequence, skip it.
  // if ( s!=null ) {
  // s.propagateCancellation();
  // }
  // }

  private <T extends Task> T instantiateDefault(Class<T> cls)
      throws InstantiationException, IllegalAccessException
  {
    if (TBB.GATHER_STATISTICS)
    {
      ++_statistics.reflect_newinstance_count;
    }
    return cls.newInstance();
  }

  // int num_workers = arena.prefix().number_of_workers;
  // for ( int i = 0; i < num_workers; ++i ) {
  // Scheduler s = arena.prefix()._workerList[i].scheduler;
  // // If the worker is in the middle of its startup sequence, skip it.
  // if ( s!=null ) {
  // s._localCancelCount = cancel_count;
  // }
  // }

  public final <T extends Task> T allocateTask(Class<T> cls, Task parent, int depth, TaskGroupContext context,
                                               Object[] arguments)
  {
    return allocateTask(taskFactory(cls), parent, depth, context, arguments);
  }

  public final <T extends Task> Factory<T> taskFactory(final Class<T> cls)
  {
    return new Factory<T>()
    {
      @Override
      public T construct(Object... arguments)
      {
        try
        {
          if (arguments == null || arguments.length == 0)
            return instantiateDefault(cls);
          else
            return instantiate(cls, arguments);
        }
        catch (InstantiationException e)
        {
          throw new RuntimeException(e);
        }
        catch (IllegalAccessException e)
        {
          throw new RuntimeException(e);
        }
      }
    };
  }

  public final <T extends Task> T allocateTask(Factory<T> factory, Task parent, int depth, TaskGroupContext context,
                                               Object[] arguments)
  {
    if (TBB.GATHER_STATISTICS)
    {
      long overhead = -System.nanoTime();
      try
      {
        return internalAllocateTask(factory, parent, depth, context, arguments);
      }
      finally
      {
        overhead += System.nanoTime();
        _statistics.allocate_overhead += overhead;
      }
    }
    else
    {
      return internalAllocateTask(factory, parent, depth, context, arguments);
    }
  }

  private <T extends Task> T internalAllocateTask(Factory<T> factory, Task parent, int depth, TaskGroupContext context,
                                                  Object[] arguments)
  {
    T t = factory.construct(arguments);
    ++_taskNodeCount;
    return allocateTask(t, parent, depth, context);
  }

  // ! Context to be associated with dummy tasks of worker threads schedulers.

  private <T extends Task> T allocateTask(T t, Task parent, int depth, TaskGroupContext context)
  {
    if (TBB.GATHER_STATISTICS)
    {
      _statistics.current_active++;
    }
    TaskBase tb = t;
    tb._link = new Pair<Task, TaskPrefix>(t);
    TaskPrefix p = new TaskPrefix(tb._link);
    p._context = context;
    p._owner = this;
    p._refCount.set(0);
    p._depth = depth;
    p._parent = parent;
    // In TBB 3.0 and later, the constructor for task sets extra_state to
    // indicate the version of the tbb/task.h header.
    // In TBB 2.0 and earlier, the constructor leaves extra_state as zero.
    // p.extra_state = 0;
    p._taskProxy = false;
    p._refCountActive = false;
    p._affinity = 0;
    p._state = Task.State.allocated;
    return t;
  }

  public final void freeTask(Task t)
  {
    if (TBB.GATHER_STATISTICS)
    {
      _statistics.current_active--;
    }
    TaskPrefix p = t.prefix();
    p._depth = 0xDEADBEEF;
    p._refCount.set(0xDEADBEEF);
    p._owner = null;
    // poison_pointer(p._owner);
    if (TBB.USE_ASSERT) assert t.state() == Task.State.executing || t.state() == Task.State.allocated;
    p._state = Task.State.freed;
    deallocateTask(t);
  }

  /**
   * Return task object to the memory allocator.
   *
   * @param t
   */
  private void deallocateTask(Task t)
  {
    TaskBase tb = t;
    TaskPrefix p = tb.prefix();
    // p._state = 0xFF;
    // p.extra_state = 0xFF;
    p._next[0] = null;
    // poison_pointer(p._next);
    // NFS_Free((char*)&t-task_prefix_reservation_size);
    p._link.first = null;
    p._link.second = null;

    p._link = tb._link = null;
    _taskNodeCount -= 1;
  }

  public final void propagateCancellation()
  {
    synchronized (_contextListHead)
    {
      // Acquire fence is necessary to ensure that the subsequent
      // node->_next load
      // returned the correct value in case it was just inserted in
      // another thread.
      // The fence also ensures visibility of the correct my_parent value.
      ListNode<TaskGroupContext> node = _contextListHead._next.get();
      while (node != _contextListHead)
      {
        TaskGroupContext ctx = node._link;
        // The absence of acquire fence while reading
        // _cancellationRequested may result
        // in repeated traversals of the same parents chain if another
        // group (precedent or
        // descendant) belonging to the tree being canceled sends
        // cancellation request of
        // its own around the same time.
        if (ctx._cancellationRequested.get() == 0)
        {
          ctx.propagateCancellationFromAncestors();
        }
        node = node._next.get();
        if (TBB.USE_ASSERT) assert ctx.isAlive() : "Walked into a destroyed context while propagating cancellation";
      }
    }
  }

  /**
   * Propagates cancellation down the tree of dependent contexts by walking
   * each thread's local list of contexts.
   *
   * @param ctx
   */
  public final void propagateCancellation(TaskGroupContext ctx)
  {
    if (TBB.USE_ASSERT) assert ctx._cancellationRequested.get() != 0 : "No cancellation request in the context";
    // The whole propagation algorithm is under the lock in order to ensure
    // correctness
    // in case of parallel cancellations at the different levels of the
    // _context tree.
    // See the note 2 at the bottom of the file.
    synchronized (_schedulerListHead)
    {
      // Advance global cancellation _state
      int cancel_count = _globalCancelCount.getAndIncrement() + 1;

      // First propagate to workers using arena to access their _context
      // lists
      propagateWorkerCancellation();

      // Then propagate to masters using the global list of master's
      // schedulers
      ListNode<Scheduler> node = _schedulerListHead._next.get();
      while (node != _schedulerListHead)
      {
        node._link.propagateCancellation();
        node = node._next.get();
      }

      // Now sync up the local counters
      syncWorkerCancellation(cancel_count);

      node = _schedulerListHead._next.get();
      while (node != _schedulerListHead)
      {
        node._link._localCancelCount = cancel_count;
        node = node._next.get();
      }
    }
  }

  protected abstract void propagateWorkerCancellation();

  protected abstract void syncWorkerCancellation(int cancel_count);

  public int numberOfWorkers()
  {
    return 0;
  }

  protected void setWorkerCount(int number_of_threads)
  {
  }

  protected MailOutbox getMailbox(int id)
  {
    return null;
  }

  protected void attachMailbox(int l)
  {
    // __TBB_ASSERT(id>0,NULL);
    _inbox.attach(getMailbox(l));
    _affinityId = l;
  }

  protected void configureWorker(int l)
  {

  }

  private void freeScheduler()
  {
    // if( in_arena() ) {
    // #if __TBB_TASK_DEQUE
    // acquire_task_pool();
    // leave_arena();
    // #else /* !__TBB_TASK_DEQUE */
    // leave_arena(/*compress=*/false);
    // #endif /* !__TBB_TASK_DEQUE */
    // }
    TaskGroupContext context = dummyTask().prefix()._context;
    // Only master thread's dummy task has a _context
    if (context != dummy_context)
    {
      // ! \todo Add assertion that master's dummy task _context does not
      // have children
      // _context.task_group_context::~task_group_context();
      // NFS_Free(_context);
      dummyTask().prefix()._context = null;
      synchronized (_schedulerListHead)
      {
        _node._next.get()._prev.lazySet(_node._prev.get());
        _node._prev.get()._next.lazySet(_node._next.get());
      }
    }

    freeTask(dummyTask());

    if (TBB.GATHER_STATISTICS)
    {
      _theStatistics.record(_statistics);
    }
    Governor.signOff(this);
  }

  // ! Notify any entry observers that have been created since the last call
  // by this thread.
  public final void notifyEntryObservers()
  {
    _localLastObserverProxy = ObserverProxy.processList(_localLastObserverProxy, isWorker(), /*
																									 * is_entry=
																									 */
                                                        true);
  }

  protected final void notifyExitObservers(boolean is_worker)
  {
    ObserverProxy.processList(_localLastObserverProxy, is_worker, /* is_entry= */ false);
  }

  public void cleanupMaster()
  {
    LOG.entering(getClass().getName(), "cleanupMaster");
    try
    {
      Scheduler s = this; // for similarity with cleanup_worker
      // __TBB_ASSERT( s.dummy_slot.task_pool,
      // "cleaning up master with missing task pool" );
      s.notifyExitObservers(/* is_worker= */false);
      s.terminateWorkers();
      // #if __TBB_TASK_DEQUE
      // if( arena_slot->task_pool != EMPTY_TASK_POOL && arena_slot->head <
      // arena_slot->tail )
      // s.wait_for_all( *_dummyTask, NULL );
      s.waitForAll(dummyTask(), null);
      // #endif /* __TBB_TASK_DEQUE */
      s.freeScheduler();
      // Governor::finish_with_arena();
    }
    finally
    {
      LOG.exiting(getClass().getName(), "cleanupMaster");
    }
  }

  protected void terminateWorkers()
  {
  }

  public static class TaskListBase
  {
    protected TaskListBase()
    {
    }

    // ! Push task onto back of list.
    protected void push_back(Task[] next, Task task)
    {
      task.prefix()._next[0] = null;
      next[0] = task;
      next = task.prefix()._next;
    }

    // ! Pop the front task from the list.
    protected Task pop_front(Task[] first, Task[] next)
    {
      Task result = first[0];
      first[0] = result.prefix()._next[0];
      if (first[0] == null)
      {
        next = first;
      }
      return result;
    }
  }

  public static class TaskBase
  {

    Pair<Task, TaskPrefix> _link;

    protected TaskBase()
    {
    }

    protected static void spawn(Scheduler s, Task child)
    {
      s.spawn(child, child.prefix()._next);
    }

    protected static void spawnAndWaitForAll(Task p, Task t, Task[] next)
    {
      Scheduler s = Governor.localScheduler();
      if (t != null)
      {
        if (t.prefix()._next != next)
        {
          s.spawn(t.prefix()._next[0], next);
        }
      }
      s.waitForAll(p, t);
    }

    protected static void spawnRootAndWait(Task root)
    {
      root.owner().spawnRootAndWait(root, root.prefix()._next);
    }

    protected <T extends Task> T allocateContinuation(Class<T> cls, Object... arguments)
        throws InstantiationException,
               IllegalAccessException
    {
      Scheduler s;
      if (TBB.RELAXED_OWNERSHIP)
      {
        s = Governor.localScheduler();
      }
      else
      {
        s = owner();
      }
      Task parent = this.parent();
      this.prefix()._parent = null;
      return s.allocateTask(cls, parent, this.prefix()._depth, this.prefix()._context, arguments);
    }

    protected <T extends Task> T allocateContinuation(Factory<T> cls, Object... arguments)
    {
      Scheduler s;
      if (TBB.RELAXED_OWNERSHIP)
      {
        s = Governor.localScheduler();
      }
      else
      {
        s = owner();
      }
      Task parent = this.parent();
      this.prefix()._parent = null;
      return s.allocateTask(cls, parent, this.prefix()._depth, this.prefix()._context, arguments);
    }

    protected <T extends Task> T allocateAdditionalChildOf(Task parent, Class<T> cls, Object... arguments)
        throws InstantiationException, IllegalAccessException
    {
      TaskBase parent_base = parent;
      parent_base.incrementRefCount();
      Scheduler s;
      if (TBB.RELAXED_OWNERSHIP)
      {
        s = Governor.localScheduler();
      }
      else
      {
        s = prefix()._owner;
      }
      return s.allocateTask(cls, parent, parent.prefix()._depth + 1, parent.prefix()._context, arguments);
    }

    protected <T extends Task> T allocateAdditionalChildOf(Task parent, Factory<T> cls, Object... arguments)
    {
      TaskBase parent_base = parent;
      parent_base.incrementRefCount();
      Scheduler s;
      if (TBB.RELAXED_OWNERSHIP)
      {
        s = Governor.localScheduler();
      }
      else
      {
        s = owner();
      }
      return s.allocateTask(cls, parent, parent.prefix()._depth + 1, parent.prefix()._context, arguments);
    }

    protected void destroy(Task victim)
    {
      TaskBase parent = victim.parent();
      if (parent != null)
      {
        parent.internalDecrementRefCount();
      }
      Governor.localScheduler().freeTask(victim);
    }

    protected void recycleAsContinuation()
    {
      prefix()._state = Task.State.allocated;
    }

    protected void recycleAsSafeContinuation()
    {
      prefix()._state = Task.State.recycle;
    }

    protected void recycleAsChildOf(Task new_parent)
    {
      TaskPrefix p = prefix();
      p._state = Task.State.allocated;
      p._parent = new_parent;
      p._depth = new_parent.prefix()._depth + 1;
      p._context = new_parent.prefix()._context;
    }

    protected void recycleToReexecute()
    {
      prefix()._state = Task.State.reexecute;
    }

    protected void waitForAll(Task parent, Task child)
    {
      prefix()._owner.waitForAll(parent, child);
    }

    /**
     * Scheduling depth
     *
     * @return
     */
    public final int depth()
    {
      return prefix()._depth;
    }

    protected void setDepth(int new_depth)
    {
      prefix()._depth = new_depth;
    }

    /**
     * Set reference count
     *
     * @param count
     */
    public final void setRefCount(int count)
    {
      if (TBB.USE_THREADING_TOOLS || TBB.USE_ASSERT)
      {
        internalSetRefCount(count);
      }
      else
      {
        prefix()._refCount.set(count);
      }
    }

    /**
     * Atomically increment reference count.
     * <p/>
     * Has acquire semantics
     */
    final void incrementRefCount()
    {
      prefix()._refCount.getAndIncrement();
    }

    /**
     * Atomically decrement reference count.
     * <p/>
     * Has release semantics.
     *
     * @return
     */
    final int decrementRefCount()
    {
      if (TBB.USE_THREADING_TOOLS || TBB.USE_ASSERT)
      {
        return internalDecrementRefCount();
      }
      else
      {
        return prefix()._refCount.getAndDecrement() - 1;
      }
    }

    // ------------------------------------------------------------------------
    // Debugging
    // ------------------------------------------------------------------------

    /**
     * Current execution _state
     *
     * @return
     */
    public final Task.State state()
    {
      return prefix()._state;
    }

    /**
     * The internal reference count.
     *
     * @return
     */
    public final int ref_count()
    {
      return prefix()._refCount.get();
    }

    /**
     * @return
     */
    protected final Scheduler owner()
    {
      return prefix()._owner;
    }

    // ------------------------------------------------------------------------
    // Affinity
    // ------------------------------------------------------------------------

    /**
     * Set affinity for this task.
     *
     * @param id
     */
    public final void setAffinity(int id)
    {
      prefix()._affinity = id;
    }

    /**
     * Current affinity of this task
     *
     * @return
     */
    public final int affinity()
    {
      return prefix()._affinity;
    }

    // ------------------------------------------------------------------------
    // private fields and methods
    // ------------------------------------------------------------------------

    /**
     * task on whose behalf this task is working, or NULL if this is a root.
     *
     * @return
     */
    public final Task parent()
    {
      return prefix()._parent;
    }

    /**
     * Shared _context that is used to communicate asynchronous _state changes
     *
     * @return
     */
    public final TaskGroupContext context()
    {
      return prefix()._context;
    }

    /**
     * Set reference count
     *
     * @param count
     */
    final void internalSetRefCount(int count)
    {
      if (TBB.USE_ASSERT) assert count >= 0 : "count must not be negative";
      if (TBB.USE_ASSERT) assert !prefix()._refCountActive : "_refCount race detected";
      // ITT_NOTIFY(sync_releasing, &prefix()._refCount);
      prefix()._refCount.set(count);
    }

    /**
     * Decrement reference count and return true if non-zero.
     *
     * @return
     */
    final int internalDecrementRefCount()
    {
      int k = prefix()._refCount.getAndDecrement();
      if (TBB.USE_ASSERT) assert k >= 1 : "task's reference count underflowed";
      // if( k==1 )
      // ITT_NOTIFY( sync_acquired, &prefix()._refCount );
      return k - 1;
    }

    /**
     * Get reference to corresponding task_prefix.
     *
     * @return
     */
    public final TaskPrefix prefix()
    {
      if (_link == null)
      {
        Task t = _emptyTaskFactory.construct();
        TaskBase tb = t;
        tb._link = new Pair<Task, TaskPrefix>(t);
        TaskPrefix p = new TaskPrefix(tb._link);
        p._context = null;
        p._owner = null;
        p._refCount.set(0);
        p._depth = 0;
        p._parent = null;
        p._taskProxy = false;
        p._refCountActive = false;
        p._affinity = 0;
        p._state = Task.State.allocated;
        return p;

      }
      return _link.second;
    }
  }

  public static class TaskSchedulerBase
  {
    protected TaskSchedulerBase()
    {
    }

    protected Scheduler initializeScheduler(int number_of_threads)
    {
      // Double-check
      TBB.runOneTimeInitialization();
      Scheduler s = Governor._theTLS.get();
      if (s != null)
      {
        s._refCount += 1;
      }
      else
      {
        try
        {
          s = Scheduler.createMasterScheduler(number_of_threads);
        }
        catch (InstantiationException e)
        {
          LOG.throwing(getClass().getName(), "initializeScheduler", e);
          throw new RuntimeException(e);
        }
        catch (IllegalAccessException e)
        {
          LOG.throwing(getClass().getName(), "initializeScheduler", e);
          throw new RuntimeException(e);
        }
      }
      return s;
    }

    protected void terminate(Scheduler s)
    {
      if (--(s._refCount) == 0)
      {
        s.cleanupMaster();
        if (TBB.GATHER_STATISTICS)
        {
          System.out.println(s._statistics);
        }
      }

    }
  }

  public static class TaskGroupContextBase
  {

    protected TaskGroupContextBase()
    {
    }

    protected void init_node(Scheduler s, ListNode<TaskGroupContext> my_node)
    {
      // Backward links are used by this thread only, thus no fences are
      // necessary
      my_node._prev.lazySet(s._contextListHead);
      s._contextListHead._next.get()._prev.lazySet(my_node);
      // The only operation on the thread local list of contexts that may
      // be performed
      // concurrently is its traversal by another thread while propagating
      // cancellation
      // request. Therefore the release fence below is necessary to ensure
      // that the new
      // value of _node._next is visible to the traversing thread
      // after it reads new value of v->_contextListHead._next.
      my_node._next.set(s._contextListHead._next.get());
      s._contextListHead._next.set(my_node);
    }
  }

  static
  {
    _schedulerListHead = new ListNode<Scheduler>(null);
    _schedulerListHead._next.lazySet(_schedulerListHead);
    _schedulerListHead._prev.lazySet(_schedulerListHead);
    _globalCancelCount = new AtomicInteger();
    _theStatistics = new Statistics();
    _emptyTaskFactory = new Factory<EmptyTask>()
    {
      public EmptyTask construct(Object... arguments)
      {
        return new EmptyTask();
      }
    };
  }

}

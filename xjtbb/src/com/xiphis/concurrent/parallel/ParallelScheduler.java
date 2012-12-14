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
package com.xiphis.concurrent.parallel;

import com.xiphis.concurrent.Task;
import com.xiphis.concurrent.TaskScheduler;
import com.xiphis.concurrent.internal.FastRandom;
import com.xiphis.concurrent.internal.Governor;
import com.xiphis.concurrent.internal.MailOutbox;
import com.xiphis.concurrent.internal.Scheduler;
import com.xiphis.concurrent.internal.TBB;
import com.xiphis.concurrent.internal.TaskPrefix;
import com.xiphis.concurrent.internal.TaskProxy;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ParallelScheduler extends com.xiphis.concurrent.internal.Scheduler
{
  private static final Logger LOG = TBB.LOG;

  private static final int min_task_pool_size = 64;
  private static final int null_arena_index = ~0;
  private static final Object _theArenaMutex = new Object();
  private static Arena _theArena;
  private final int _stealingThreshold = 128;
  private final ArenaSlot _dummySlot = new ArenaSlot();
  private FastRandom _random;
  private Arena _arena;
  private int _arenaIndex;
  private ArenaSlot _arenaSlot = _dummySlot;
  private int _taskPoolSize;

  public ParallelScheduler()
      throws InstantiationException, IllegalAccessException
  {
    super();
    init();
  }

  public ParallelScheduler(Arena arena)
      throws InstantiationException, IllegalAccessException
  {
    super();
    _arena = arena;
    init();
  }

  /**
   * Obtain the instance of _arena to register a new master thread
   * <p/>
   * If there is no active _arena, create one.
   *
   * @param number_of_threads
   * @return
   */
  static Arena obtainArena(int number_of_threads)
  {
    Arena a;
    synchronized (_theArenaMutex)
    {
      a = _theArena;
      if (a != null)
      {
        a.prefix()._numberOfMasters += 1;
        return a;
      }
      else
      {
        if (number_of_threads == TaskScheduler.automatic)
        {
          number_of_threads = TaskScheduler.defaultThreads();
        }
        LOG.log(Level.INFO, "Initializing JavaTBB Parallel Scheduler with {0} worker threads.", number_of_threads);
        a = new Arena(2 * number_of_threads, number_of_threads - 1);
        if (TBB.USE_ASSERT) assert a.prefix()._numberOfMasters == 1;
        // Publish the Arena.
        // A memory release fence is not required here, because workers
        // have not started yet,
        // and concurrent masters inspect _theArena while holding
        // _theArenaMutex.
        if (TBB.USE_ASSERT) assert _theArena == null;
        _theArena = a;
      }
    }
    // Attach threads to workers
    if (number_of_threads > 1)
    { // there should be worker threads
      a.prefix()._workerList[0].startOneWorkerThread();
    }
    return a;
  }

  static void setTaskRefCount(Task t, int i)
  {
    Scheduler.setRefCount(t.prefix(), i);
  }

  static com.xiphis.concurrent.internal.Scheduler createWorkerHeap(WorkerDescriptor w)
      throws InstantiationException,
             IllegalAccessException
  {
    // __TBB_ASSERT( (uintptr)w._scheduler+1<2u, NULL );
    int n = w._arena.prefix().number_of_workers;
    WorkerDescriptor[] worker_list = w._arena.prefix()._workerList;
    // __TBB_ASSERT( &w >= _workerList, NULL );
    // unsigned i = unsigned(&w - _workerList);
    int i = 0;
    while (worker_list[i] != w)
    {
      ++i;
    }
    if (TBB.USE_ASSERT) assert i < n;

    // START my children
    if (2 * i + 1 < n)
    {
      // Have a left child, so start it.
      worker_list[2 * i + 1].startOneWorkerThread();
      if (2 * i + 2 < n)
      {
        // Have a right child, so start it.
        worker_list[2 * i + 2].startOneWorkerThread();
      }
    }

    com.xiphis.concurrent.internal.Scheduler s = createWorkerScheduler(i, w._arena);

    // Attempt to publish worker
    // ITT_NOTIFY(sync_releasing, &w._scheduler);
    // Note: really need only release fence on the compare-and-swap.
    if (!w._scheduler.compareAndSet(null, s, false, false))
    {
      // Master thread has already commenced terminate_workers() and not
      // waited for us to respond.
      // Thus we are responsible for cleaning up ourselves.
      setRefCount(s.dummyTask().prefix(), 0);
      // Do not register scheduler in thread local storage, because the
      // storage may be gone.
    }
    else
    {
      if (TBB.USE_ASSERT) assert w._scheduler.getReference() == s;
      Governor.signOn(s);
    }
    return s;
  }

  private void init()
  {
    _random = new FastRandom((int) ((System.nanoTime() * (Thread.currentThread().getId() + 1) / 255)));
    _dummySlot._taskPool.set(allocateTaskPool(min_task_pool_size));
    _arenaIndex = null_arena_index;
  }

  private boolean inArena()
  {
    return _arenaSlot != _dummySlot;
  }

  /**
   * Actions common to enter_arena and try_enter_arena
   */
  private void doEnterArena()
  {
    _arenaSlot = _arena._slot[_arenaIndex];
    if (TBB.USE_ASSERT) assert _arenaSlot.head == _arenaSlot.tail : "task deque of a free _slot must be empty";
    _arenaSlot.head = _dummySlot.head;
    _arenaSlot.tail = _dummySlot.tail;
    // Release signal on behalf of previously spawned tasks (when this
    // thread was not in _arena yet)
    // ITT_NOTIFY(sync_releasing, _arenaSlot);
    _arenaSlot._taskPool.set(_dummySlot._taskPool.get());
    // We'll leave _arena only when it's empty, so clean up local instances
    // of indices.
    _dummySlot.head = _dummySlot.tail = 0;

  }

  /**
   * Used by workers to enter the _arena
   * <p/>
   * Does not lock the task pool in case if _arena _slot has been successfully
   * grabbed.
   */
  private void enterArena()
  {
    if (TBB.USE_ASSERT) assert isWorker() : "only workers should use enter_arena()";
    if (TBB.USE_ASSERT) assert _arena != null : "no arena: initialization not completed?";
    if (TBB.USE_ASSERT) assert !inArena() : "worker already in arena?";
    if (TBB.USE_ASSERT) assert _arenaIndex < _arena.prefix().number_of_workers : "invalid worker arena slot index";
    if (TBB.USE_ASSERT)
      assert _arena._slot[_arenaIndex]._taskPool.get() == ArenaSlot.EMPTY_TASK_POOL : "someone else grabbed my arena slot?";
    doEnterArena();
  }

  /**
   * Used by masters to try to enter the _arena
   * <p/>
   * Does not lock the task pool in case if _arena _slot has been successfully
   * grabbed.
   */
  void tryEnterArena()
  {
    if (TBB.USE_ASSERT) assert !isWorker() : "only masters should use try_enter_arena()";
    if (TBB.USE_ASSERT) assert _arena != null : "no arena: initialization not completed?";
    if (TBB.USE_ASSERT) assert !inArena() : "master already in arena?";
    if (TBB.USE_ASSERT)
      assert _arenaIndex >= _arena.prefix().number_of_workers && _arenaIndex < _arena.prefix()._numberOfSlots : "invalid arena slot hint value";

    int h = _arenaIndex;
    // We do not lock task pool upon successful entering _arena
    if (_arena._slot[h]._taskPool.get() != ArenaSlot.EMPTY_TASK_POOL
        || !_arena._slot[h]._taskPool.compareAndSet(ArenaSlot.EMPTY_TASK_POOL, ArenaSlot.LOCKED_TASK_POOL))
    {
      // Hinted _arena _slot is already busy, try some of the others at
      // random
      int first = _arena.prefix().number_of_workers;
      int last = _arena.prefix()._numberOfSlots;
      int n = last - first - 1;
      // / TODO Is this limit reasonable?
      int max_attempts = last - first;
      for (; ; )
      {
        int k = first + _random.get() % n;
        if (k >= h)
        {
          ++k;
        } // Adjusts random distribution to exclude previously tried
        // _slot
        h = k;
        if (_arena._slot[h]._taskPool.get() == ArenaSlot.EMPTY_TASK_POOL
            && _arena._slot[h]._taskPool.compareAndSet(ArenaSlot.EMPTY_TASK_POOL, ArenaSlot.LOCKED_TASK_POOL))
        {
          break;
        }
        if (--max_attempts == 0)
        {
          // After so many attempts we are still unable to find a
          // vacant _arena _slot.
          // Cease the vain effort and work outside of _arena for a
          // while.
          return;
        }
      }
    }
    // Successfully claimed a _slot in the _arena.
    // ITT_NOTIFY(sync_acquired, &_arena->_slot[h]);
    if (TBB.USE_ASSERT)
      assert _arena._slot[h]._taskPool.get() == ArenaSlot.LOCKED_TASK_POOL : "Arena slot is not actually acquired";
    _arenaIndex = h;
    doEnterArena();
    attachMailbox(h + 1);
  }

  /**
   * Leave the _arena
   */
  private void leaveArena()
  {
    if (TBB.USE_ASSERT) assert inArena() : "Not in arena";
    // Do not reset _arenaIndex. It will be used to (attempt to) re-acquire
    // the _slot next time
    if (TBB.USE_ASSERT) assert _arena._slot[_arenaIndex] == _arenaSlot : "Arena slot and slot index mismatch";
    if (TBB.USE_ASSERT) assert _arenaSlot._taskPool.get() == ArenaSlot.LOCKED_TASK_POOL;
    if (TBB.USE_ASSERT)
      assert _arenaSlot.head == _arenaSlot.tail : "Cannot leave arena when the task pool is not empty";

    if (!isWorker())
    {
      _affinityId = 0;
      _inbox.detach();
    }
    // ITT_NOTIFY(sync_releasing, &_arena->_slot[_arenaIndex]);
    _arenaSlot._taskPool.set(ArenaSlot.EMPTY_TASK_POOL);
    // __TBB_store_with_release( _arenaSlot->_taskPool, EMPTY_TASK_POOL );
    _arenaSlot = _dummySlot;
  }

  /**
   * Locks victim's task pool, and returns pointer to it. The pointer can be
   * NULL.
   *
   * @param victim_arena_slot
   * @return
   */
  private Task[] lockTaskPool(ArenaSlot victim_arena_slot)
  {
    /**
     * ATTENTION: This method is mostly the same as
     * GenericScheduler::acquire_task_pool(), with a little different logic
     * of _slot state checks (_slot can be empty, locked or point to any task
     * pool other than ours, and asynchronous transitions between all these
     * states are possible). Thus if any of them is changed, consider
     * changing the counterpart as well
     **/
    Task[] victim_task_pool;
    // atomic_backoff backoff;
    for (; ; )
    {
      victim_task_pool = victim_arena_slot._taskPool.get();
      // TODO: Investigate the effect of bailing out on the locked pool
      // without trying to lock it.
      // When doing this update assertion in the end of the method.
      if (victim_task_pool == ArenaSlot.EMPTY_TASK_POOL)
      {
        // The victim thread emptied its task pool - nothing to lock
        break;
      }
      if (victim_task_pool != ArenaSlot.LOCKED_TASK_POOL
          && victim_arena_slot._taskPool.compareAndSet(victim_task_pool, ArenaSlot.LOCKED_TASK_POOL))
      {
        // We've locked victim's task pool
        // ITT_NOTIFY(sync_acquired, victim_arena_slot);
        break;
      }
      // Someone else acquired a lock, so pause and do exponential
      // backoff.
      // backoff.pause();
      TBB.Yield();
    }
    if (TBB.USE_ASSERT)
      assert victim_task_pool == ArenaSlot.EMPTY_TASK_POOL || victim_arena_slot._taskPool.get() == ArenaSlot.LOCKED_TASK_POOL
          && victim_task_pool != ArenaSlot.LOCKED_TASK_POOL : "not really locked victim's task pool?";
    return victim_task_pool;
  } // GenericScheduler::lock_task_pool

  /**
   * Unlocks victim's task pool
   *
   * @param victim_arena_slot
   * @param victim_task_pool
   */
  private void unlockTaskPool(ArenaSlot victim_arena_slot, Task[] victim_task_pool)
  {
    if (TBB.USE_ASSERT) assert victim_arena_slot != null : "empty victim arena slot pointer";
    if (TBB.USE_ASSERT)
      assert victim_arena_slot._taskPool.get() == ArenaSlot.LOCKED_TASK_POOL : "victim arena slot is not locked";
    // ITT_NOTIFY(sync_releasing, victim_arena_slot);
    victim_arena_slot._taskPool.set(victim_task_pool);
  }

  private Task prepareForSpawning(Task t)
  {
    if (TBB.USE_ASSERT)
      assert t.state() == Task.State.allocated : "attempt to spawn task that is not in 'allocated' state";
    TaskPrefix tp = t.prefix();
    setOwner(tp, this);
    setState(tp, Task.State.ready);
    if (TBB.USE_ASSERT)
    {
      Task parent = t.parent();
      if (parent != null)
      {
        int ref_count = parent.ref_count();
        assert ref_count >= 0 : "attempt to spawn task whose parent has a ref_count<0";
        assert ref_count != 0 : "attempt to spawn task whose parent has a ref_ount==0 (forgot to set_ref_count?)";
        setRefCountActive(parent.prefix(), true);
      }
    }
    int dst_thread = t.affinity();
    if (dst_thread != 0 && dst_thread != _affinityId)
    {
      return allocateTaskProxy(t, _arena.mailbox(dst_thread));
    }
    return t;
  }

  /**
   * ATTENTION: This method is mostly the same as
   * GenericScheduler::lock_task_pool(), with a little different logic of _slot
   * state checks (_slot is either locked or points to our task pool). Thus if
   * either of them is changed, consider changing the counterpart as well.
   */
  private void acquireTaskPool()
  {
    if (!inArena())
    {
      return; // we are not in _arena - nothing to lock
    }
    // atomic_backoff backoff;
    for (; ; )
    {
      if (TBB.USE_ASSERT)
      {
        assert _arenaSlot == _arena._slot[_arenaIndex] : "invalid arena slot index";
        // Local copy of the _arena _slot task pool pointer is necessary
        // for the next
        // assertion to work correctly to exclude asynchronous state
        // transition effect.
        Task[] tp = _arenaSlot._taskPool.get();
        assert tp == ArenaSlot.LOCKED_TASK_POOL || tp == _dummySlot._taskPool.get() : "slot ownership corrupt?";
      }
      if (_arenaSlot._taskPool.get() != ArenaSlot.LOCKED_TASK_POOL
          && _arenaSlot._taskPool.compareAndSet(_dummySlot._taskPool.get(), ArenaSlot.LOCKED_TASK_POOL))
      {
        // We acquired our own _slot
        // ITT_NOTIFY(sync_acquired, _arenaSlot);
        break;
      }
      // Someone else acquired a lock, so pause and do exponential
      // backoff.
      TBB.Yield();
      // backoff.pause();
    }
    if (TBB.USE_ASSERT)
      assert _arenaSlot._taskPool.get() == ArenaSlot.LOCKED_TASK_POOL : "not really acquired task pool";
  } // GenericScheduler::acquire_task_pool

  private void releaseTaskPool()
  {
    if (!inArena())
    {
      return; // we are not in _arena - nothing to unlock
    }
    if (TBB.USE_ASSERT) assert _arenaSlot != null : "we are not in arena";
    if (TBB.USE_ASSERT) assert _arenaSlot._taskPool.get() == ArenaSlot.LOCKED_TASK_POOL : "arena slot is not locked";
    // ITT_NOTIFY(sync_releasing, _arenaSlot);
    _arenaSlot._taskPool.set(_dummySlot._taskPool.get());
  }

  private Task[] allocateTaskPool(int n)
  {
    if (TBB.USE_ASSERT) assert n > _taskPoolSize : "Cannot shrink the task pool";
    _taskPoolSize = n;
    Task[] new_pool = new Task[_taskPoolSize];
    return new_pool;
  }

  private void grow(int new_size)
  {
    if (TBB.USE_ASSERT) assert assertOkay();
    if (new_size < 2 * _taskPoolSize)
    {
      new_size = 2 * _taskPoolSize;
    }
    Task[] new_pool = allocateTaskPool(new_size); // updates _taskPoolSize
    Task[] old_pool = _dummySlot._taskPool.get();
    acquireTaskPool(); // requires the old _dummySlot._taskPool value
    int new_tail = _arenaSlot.tail - _arenaSlot.head;
    if (TBB.USE_ASSERT) assert new_tail <= _taskPoolSize : "new task pool is too short";
    System.arraycopy(old_pool, _arenaSlot.head, new_pool, 0, _arenaSlot.tail);
    _arenaSlot.head = 0;
    _arenaSlot.tail = new_tail;
    _dummySlot._taskPool.set(new_pool);
    releaseTaskPool(); // updates the task pool pointer in our _arena _slot
    // free_task_pool( old_pool );
    if (TBB.USE_ASSERT) assert assertOkay();
  }

  @Override
  protected void schedulerSpawn(Task first, Task[] next)
  {
    if (TBB.USE_ASSERT) assert assertOkay();
    if (getNextPtr(first.prefix()) == next)
    {
      // Single task is being spawned
      if (_arenaSlot.tail == _taskPoolSize)
      {
        // 1 compensates for head possibly temporarily incremented by a
        // thief
        if (_arenaSlot.head > 1)
        {
          // Move the busy part of the deque to the beginning of the
          // allocated space
          acquireTaskPool();
          _arenaSlot.tail -= _arenaSlot.head;
          Task[] tp = _dummySlot._taskPool.get();
          System.arraycopy(tp, _arenaSlot.head, tp, 0, _arenaSlot.tail);
          _arenaSlot.head = 0;
          releaseTaskPool();
        }
        else
        {
          grow(_taskPoolSize + 1);
        }
      }
      _dummySlot._taskPool.get()[_arenaSlot.tail] = prepareForSpawning(first);
      // ITT_NOTIFY(sync_releasing, _arenaSlot);
      // The following store with release is required on ia64 only
      int new_tail = _arenaSlot.tail + 1;
      _arenaSlot.tail = new_tail;
      // __TBB_store_with_release( _arenaSlot->tail, new_tail );
      if (TBB.USE_ASSERT) assert _arenaSlot.tail <= _taskPoolSize : "task deque end was overwritten";
    }
    else
    {
      // Task list is being spawned
      ArrayList<Task> tasks = new ArrayList<Task>(64);
      Task t_next = null;
      for (Task t = first; ; t = t_next)
      {
        // After prepare_for_spawning returns t may already have been
        // destroyed.
        // So milk it while it is alive.
        TaskPrefix tp = t.prefix();
        Task[] tp_next = getNextPtr(tp);
        boolean end = tp_next == next;
        t_next = tp_next[0];
        tasks.add(prepareForSpawning(t));
        if (end)
        {
          break;
        }
      }
      int num_tasks = tasks.size();
      if (TBB.USE_ASSERT) assert _arenaIndex != null_arena_index : "invalid arena slot index";
      if (_arenaSlot.tail + num_tasks > _taskPoolSize)
      {
        // 1 compensates for head possibly temporarily incremented by a
        // thief
        int new_size = _arenaSlot.tail - _arenaSlot.head + num_tasks + 1;
        if (new_size <= _taskPoolSize)
        {
          // Move the busy part of the deque to the beginning of the
          // allocated space
          acquireTaskPool();
          _arenaSlot.tail -= _arenaSlot.head;
          Task[] tp = _dummySlot._taskPool.get();
          System.arraycopy(tp, _arenaSlot.head, tp, 0, _arenaSlot.tail);
          _arenaSlot.head = 0;
          releaseTaskPool();
        }
        else
        {
          grow(new_size);
        }
      }
      Task[] tp = _dummySlot._taskPool.get();
      int new_tail = _arenaSlot.tail + tasks.size();
      int i = new_tail;
      for (Task t : tasks)
      {
        tp[--i] = t;
      }
      _arenaSlot.tail = new_tail;
      // The following store with release is required on ia64 only
      // __TBB_store_with_release(_arenaSlot.tail, new_tail);
      if (TBB.USE_ASSERT) assert _arenaSlot.tail <= _taskPoolSize : "task deque end was overwritten";
    }
    if (!inArena())
    {
      if (isWorker())
      {
        enterArena();
      }
      else
      {
        tryEnterArena();
      }
    }
    _arena.markPoolFull();
    if (TBB.USE_ASSERT) assert assertOkay();

    // TBB_TRACE(("%p.internal_spawn exit\n", this ));
  }

  @Override
  protected Task getTask(int depth)
  {
    Task result = null;
    retry:
    for (; ; )
    {
      --_arenaSlot.tail;
      // __TBB_rel_acq_fence();
      if (_arenaSlot.head > _arenaSlot.tail)
      {
        acquireTaskPool();
        if (_arenaSlot.head <= _arenaSlot.tail)
        {
          // The thief backed off - grab the task
          // __TBB_ASSERT_VALID_TASK_PTR(
          // _dummySlot._taskPool[_arenaSlot->tail] );
          Task[] tp = _dummySlot._taskPool.get();
          result = tp[_arenaSlot.tail];
          tp[_arenaSlot.tail] = null;
          // __TBB_POISON_TASK_PTR(
          // _dummySlot._taskPool[_arenaSlot->tail] );
        }
        else
        {
          if (TBB.USE_ASSERT)
            assert _arenaSlot.head == _arenaSlot.tail + 1 : "victim/thief arbitration algorithm failure";
        }
        if (_arenaSlot.head < _arenaSlot.tail)
        {
          releaseTaskPool();
        }
        else
        {
          // In any case the deque is empty now, so compact it
          _arenaSlot.head = _arenaSlot.tail = 0;
          if (inArena())
          {
            leaveArena();
          }
        }
      }
      else
      {
        // __TBB_ASSERT_VALID_TASK_PTR(
        // _dummySlot._taskPool[_arenaSlot->tail] );
        Task[] tp = _dummySlot._taskPool.get();
        result = tp[_arenaSlot.tail];
        tp[_arenaSlot.tail] = null;
        // __TBB_POISON_TASK_PTR( _dummySlot._taskPool[_arenaSlot->tail]
        // );
      }
      if (result != null && isProxy(result))
      {
        result = stripProxy(result);
        if (result == null)
        {
          continue retry;
        }
        if (TBB.GATHER_STATISTICS)
        {
          ++_statistics.proxy_execute_count;
        }
        // Task affinity has changed.
        setRunningTask(result);
        result.noteAffinity(_affinityId);
      }
      return result;
    }
  } // GenericScheduler::get_task

  @Override
  protected boolean isWorker()
  {
    return _arenaIndex < _arena.prefix().number_of_workers;
  }

  /**
   * Returns true if stealing is allowed
   */
  @Override
  protected boolean canSteal()
  {
    return Thread.currentThread().getStackTrace().length < _stealingThreshold;
  }

  @Override
  protected Task stealTask(int depth)
  {
    int n = _arena.prefix()._limit;
    int k = n > 1 ? _random.get() % (n - 1) : 0;
    ArenaSlot victim;
    if (k >= _arenaIndex)
    {// Adjusts random distribution to exclude self
      victim = _arena._slot[k + 1];
    }
    else
    {
      victim = _arena._slot[k];
    }
    return stealTask(victim);
  }

  @Override
  protected boolean waitWhilePoolIsEmpty()
  {
    return _arena.waitWhilePoolIsEmpty();
  }

  @Override
  public boolean assertOkay()
  {
    if (TBB.USE_DEEP_ASSERT)
    {
      acquireTaskPool();
      Task[] tp = _dummySlot._taskPool.get();
      assert _taskPoolSize >= min_task_pool_size;
      assert _arenaSlot.head <= _arenaSlot.tail;
      for (int i = _arenaSlot.head; i < _arenaSlot.tail; ++i)
      {
        // __TBB_ASSERT( (uintptr_t)tp[i] + 1 > 1u,
        // "nil or invalid task pointer in the deque" );
        assert tp[i].state() == Task.State.ready || isProxy(tp[i]) : "task in the deque has invalid state";
      }
      releaseTaskPool();
    }
    return true;
  }

  @Override
  protected void propagateWorkerCancellation()
  {
    int num_workers = _arena.prefix().number_of_workers;
    for (int i = 0; i < num_workers; ++i)
    {
      // No fence is necessary here since the context list of worker's scheduler
      // can contain anything of interest only after the first stealing was done
      // by that worker. And doing it applies the necessary fence
      com.xiphis.concurrent.internal.Scheduler s = _arena.prefix()._workerList[i]._scheduler.getReference();
      // If the worker is in the middle of its startup sequence, skip it.
      if (s != null)
      {
        s.propagateCancellation();
      }
    }
  }

  @Override
  protected void syncWorkerCancellation(int cancel_count)
  {

    int num_workers = _arena.prefix().number_of_workers;
    for (int i = 0; i < num_workers; ++i)
    {
      com.xiphis.concurrent.internal.Scheduler s = _arena.prefix()._workerList[i]._scheduler.getReference();
      // If the worker is in the middle of its startup sequence, skip it.
      if (s != null)
      {
        setLocalCancelCount(s, cancel_count);
      }
    }
  }

  @Override
  public int numberOfWorkers()
  {
    return _arena.prefix().number_of_workers;
  }

  @Override
  protected void setWorkerCount(int number_of_threads)
  {
    _arena = obtainArena(number_of_threads);

    int last = _arena.prefix()._numberOfSlots;
    int cur_limit = _arena.prefix()._limit;
    // This _slot index assignment is just a hint to ...
    if (cur_limit < last)
    {
      // ... to prevent competition between the first few masters.
      _arenaIndex = cur_limit++;
      // In the absence of exception handling this code is a subject to data
      // race in case of multiple masters concurrently entering empty _arena.
      // But it does not affect correctness, and can only result in a few
      // masters competing for the same _arena _slot during the firstacquisition.
      // The cost of competition is low in comparison to that of oversubscription.
      _arena.prefix()._limit = cur_limit;
    }
    else
    {
      // ... to minimize the probability of competition between multiple masters.
      int first = _arena.prefix().number_of_workers;
      _arenaIndex = first + _random.get() % (last - first);
    }
  }

  @Override
  protected MailOutbox getMailbox(int id)
  {
    return _arena.mailbox(id);
  }

  @Override
  protected void configureWorker(int index)
  {
    _arenaIndex = index;
  }

  Task stealTask(ArenaSlot victim_slot)
  {
    Task[] victim_pool = lockTaskPool(victim_slot);
    if (victim_pool == null)
    {
      return null;
    }
    final int none = ~0;
    int first_skipped_proxy = none;
    Task result = null;
    retry:
    for (; ; )
    {
      ++victim_slot.head;
      // __TBB_rel_acq_fence();
      if (victim_slot.head > victim_slot.tail)
      {
        --victim_slot.head;
      }
      else
      {
        // __TBB_ASSERT_VALID_TASK_PTR( victim_pool[victim_slot.head - 1]);
        result = victim_pool[victim_slot.head - 1];
        if (isProxy(result))
        {
          TaskProxy tp = TaskProxy.class.cast(result);
          // If task will likely be grabbed by whom it was mailed to,
          // skip it.
          if ((tp.getTag() & 3) == 3 && tp.recipientIsIdle())
          {
            if (first_skipped_proxy == none)
            {
              first_skipped_proxy = victim_slot.head - 1;
            }
            result = null;
            continue retry;
          }
        }
        victim_pool[victim_slot.head - 1] = null;
        // __TBB_POISON_TASK_PTR(victim_pool[victim_slot.head - 1]);
      }
      if (first_skipped_proxy != none)
      {
        if (result != null)
        {
          victim_pool[victim_slot.head - 1] = victim_pool[first_skipped_proxy];
          victim_pool[first_skipped_proxy] = null;
          // __TBB_POISON_TASK_PTR( victim_pool[first_skipped_proxy] );
          victim_slot.head = first_skipped_proxy + 1;
          // __TBB_store_with_release( victim_slot.head, first_skipped_proxy + 1 );
        }
        else
        {
          victim_slot.head = first_skipped_proxy;
          // __TBB_store_with_release( victim_slot.head, first_skipped_proxy );
        }
      }
      unlockTaskPool(victim_slot, victim_pool);
      return result;
    }
  }

  static class Worker implements Runnable
  {
    WorkerDescriptor w;

    Worker(WorkerDescriptor w)
    {
      this.w = w;
    }

    public void run()
    {
      Thread.currentThread().setName("TBB Worker Thread");

      com.xiphis.concurrent.internal.Scheduler scheduler;
      try
      {
        scheduler = createWorkerHeap(w);
      }
      catch (InstantiationException e)
      {
        LOG.log(Level.SEVERE, "Failed to create scheduler instance", e);
        throw new RuntimeException(e);
      }
      catch (IllegalAccessException e)
      {
        LOG.log(Level.SEVERE, "Failed to create scheduler instance", e);
        throw new RuntimeException(e);
      }

      if (scheduler.dummyTask().ref_count() == 0)
      {
        // No sense to start anything because shutdown has been requested
        cleanupWorker(scheduler);
        return;
      }

      try
      {
        scheduler.waitForAll(scheduler.dummyTask(), null);
      }
      finally
      {
        cleanupWorker(scheduler);
      }
    }
  }
}

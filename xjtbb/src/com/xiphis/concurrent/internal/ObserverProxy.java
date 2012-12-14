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

import com.xiphis.concurrent.TaskSchedulerObserver;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ObserverProxy
{
  private static final Logger LOG = TBB.LOG;

  /**
   * Reference count used for garbage collection.
   * <p/>
   * 1 for reference from my task_scheduler_observer. 1 for each
   * _localLastObserverProxy that points to me. No accounting for
   * predecessor in the global list. No accounting for
   * global_last_observer_proxy that points to me.
   */
  public final AtomicInteger _gcRefCount = new AtomicInteger(1);

  /**
   * Pointer to next task_scheduler_observer
   * <p/>
   * Valid even when *this has been removed from the global list.
   */
  ObserverProxy next;

  /**
   * Pointer to previous task_scheduler_observer in global list.
   */
  ObserverProxy prev;

  /**
   * Associated _observer
   */
  public TaskSchedulerObserver _observer;

  /**
   * Account for removing reference from p. No effect if p is NULL.
   */
  public void remove_ref_slow()
  {
    int r = _gcRefCount.get();
    while (r > 1)
    {
      if (TBB.USE_ASSERT) assert r != 0;
      if (_gcRefCount.compareAndSet(r, r - 1))
      {
        // Successfully decremented count.
        return;
      }
      r = _gcRefCount.get();
    }
    if (TBB.USE_ASSERT) assert r == 1;
    // Reference count might go to zero
    Lock lock = _taskSchedulerObserverMutex.writeLock();
    try
    {
      lock.lock();
      r = _gcRefCount.decrementAndGet();
      if (r == 0)
      {
        remove_from_list();
      }
    }
    finally
    {
      lock.unlock();
    }
    if (r == 0)
    {
      if (TBB.USE_ASSERT) assert _gcRefCount.get() == -666;
      --observer_proxy_count;
      // delete this;
    }

  }

  void remove_from_list()
  {
    // Take myself off the global list.
    if (next != null)
    {
      next.prev = prev;
    }
    else
    {
      global_last_observer_proxy = prev;
    }
    if (prev != null)
    {
      prev.next = next;
    }
    else
    {
      global_first_observer_proxy = next;
    }
    prev = next = null;
    _gcRefCount.set(-666);
  }

  public ObserverProxy(TaskSchedulerObserver wo)
  {
    ++observer_proxy_count;

    // Append to the global list
    Lock lock = _taskSchedulerObserverMutex.writeLock();
    try
    {
      lock.lock();
      ObserverProxy p = global_last_observer_proxy;
      prev = p;
      if (p != null)
      {
        p.next = this;
      }
      else
      {
        global_first_observer_proxy = this;
      }
      global_last_observer_proxy = this;
    }
    finally
    {
      lock.unlock();
    }
  }

  public static ObserverProxy processList(ObserverProxy local_last, boolean is_worker, boolean is_entry)
  {
    // Pointer p marches though the list.
    // If is_entry, start with our previous list position, otherwise start
    // at beginning of list.
    ObserverProxy p = is_entry ? local_last : null;
    loop:
    for (; ; )
    {
      TaskSchedulerObserver tso = null;
      // Hold lock on list only long enough to advance to _next proxy in
      // list.
      Lock lock = _taskSchedulerObserverMutex.readLock();
      try
      {
        lock.lock();
        do
        {
          if (local_last != null && local_last._observer != null)
          {
            // 2 = 1 for _observer and 1 for local_last
            if (TBB.USE_ASSERT) assert local_last._gcRefCount.get() >= 2;
            // Can decrement count quickly, because it cannot become
            // zero here.
            local_last._gcRefCount.decrementAndGet();
            local_last = null;
          }
          else
          {
            // Use slow form of decrementing the reference count,
            // after lock is released.
          }
          if (p != null)
          {
            // We were already processing the list.
            ObserverProxy q = p.next;
            if (q != null)
            {
              // Step to _next item in list.
              p = q;
            }
            else
            {
              // At end of list.
              if (is_entry)
              {
                // Remember current position in the list, so we
                // can start at on the _next call.
                p._gcRefCount.incrementAndGet();
              }
              else
              {
                // Finishin running off the end of the list
                p = null;
              }
              break loop;
            }
          }
          else
          {
            // Starting pass through the list
            p = global_first_observer_proxy;
            if (p == null)
            {
              break loop;
            }
          }
          tso = p._observer;
        } while (tso == null);
        p._gcRefCount.incrementAndGet();
        tso._busyCount.incrementAndGet();
      }
      finally
      {
        lock.unlock();
      }
      if (TBB.USE_ASSERT) assert local_last == null || p != local_last;
      if (local_last != null)
      {
        local_last.remove_ref_slow();
      }
      // Do not hold any locks on the list while calling user's code.
      try
      {
        if (is_entry)
        {
          tso.onSchedulerEntry(is_worker);
        }
        else
        {
          tso.onSchedulerExit(is_worker);
        }
      }
      catch (Throwable e)
      {
        // Suppress exception, because user routines are supposed to be observing, not changing
        // behavior of a master or worker thread.
        LOG.log(Level.WARNING, is_entry
            ? "on_scheduler_entry threw exception"
            : "on_scheduler_exit threw exception",
                e);
      }
      int bc = tso._busyCount.decrementAndGet();
      if (TBB.USE_ASSERT) assert bc >= 0 : "_busyCount underflowed";
      local_last = p;
    }

    // Return new value to be used as local_last _next time.
    if (local_last != null)
    {
      local_last.remove_ref_slow();
    }
    if (TBB.USE_ASSERT) assert p == null || is_entry : "_busyCount underflowed";
    return p;
  }

  public static final ReadWriteLock _taskSchedulerObserverMutex = new ReentrantReadWriteLock();
  static ObserverProxy global_first_observer_proxy;
  static ObserverProxy global_last_observer_proxy;
  static int observer_proxy_count;

}

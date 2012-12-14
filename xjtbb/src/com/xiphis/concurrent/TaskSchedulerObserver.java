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
import com.xiphis.concurrent.internal.ObserverProxy;
import com.xiphis.concurrent.internal.Scheduler;
import com.xiphis.concurrent.internal.TBB;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

public abstract class TaskSchedulerObserver
{
  public final AtomicInteger _busyCount;
  ObserverProxy _proxy;

  /**
   * Construct _observer with observation disabled.
   */
  public TaskSchedulerObserver()
  {
    _proxy = null;
    _busyCount = new AtomicInteger(0);
  }

  /**
   * Enable or disable observation
   */
  public final void observe()
  {
    observe(true);
  }

  /**
   * Enable or disable observation
   *
   * @param state
   */
  public final void observe(boolean state)
  {
    if (state)
    {
      if (_proxy == null)
      {
        TBB.runOneTimeInitialization();
        _busyCount.set(0);
        _proxy = new ObserverProxy(this);
        Scheduler s = Governor.localScheduler();
        if (s != null)
        {
          // Notify newly created _observer of its own thread.
          // Any other pending observers are notified too.
          s.notifyEntryObservers();
        }
      }
    }
    else
    {
      ObserverProxy proxy = _proxy;
      if (proxy != null)
      {
        _proxy = null;
        if (TBB.USE_ASSERT) assert proxy._gcRefCount.get() >= 1 : "reference for observer missing";
        Lock lock = ObserverProxy._taskSchedulerObserverMutex.writeLock();
        try
        {
          lock.lock();
          proxy._observer = null;
        }
        finally
        {
          lock.unlock();
        }
        proxy.remove_ref_slow();
        while (_busyCount.get() != 0)
        {
          TBB.Yield();
        }
      }
    }

  }

  /**
   * True if observation is enables; false otherwise.
   *
   * @return
   */
  public final boolean is_observing()
  {
    return _proxy != null;
  }

  /**
   * Destructor
   */
  @Override
  protected void finalize()
      throws Throwable
  {
    try
    {
      observe(false);
    }
    finally
    {
      super.finalize();
    }
  }

  /**
   * Called by thread before first steal since observation became enabled
   *
   * @param is_worker
   */
  public void onSchedulerEntry(boolean is_worker)
  {
  }

  /**
   * Called by thread when it no longer takes part in task stealing.
   *
   * @param is_worker
   */
  public void onSchedulerExit(boolean is_worker)
  {
  }

}

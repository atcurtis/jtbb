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

import com.xiphis.concurrent.internal.Scheduler;
import com.xiphis.concurrent.internal.TBB;

public final class TaskScheduler extends Scheduler.TaskSchedulerBase
{
  public final static int automatic = -1;
  public final static int deferred = -2;
  private Scheduler _scheduler;

  public TaskScheduler(int number_of_threads)
  {
    initialize(number_of_threads);
  }

  public TaskScheduler()
  {
    initialize(automatic);
  }

  public static int defaultThreads()
  {
    // No memory fence required, because at worst each invoking thread calls
    // NumberOfHardwareThreads.
    int n = TBB.DefaultNumberOfThreads;
    if (n == 0)
    {
      TBB.DefaultNumberOfThreads = n = TBB.DetectNumberOfWorkers();
    }
    return n;
  }

  @Override
  protected void finalize()
      throws Throwable
  {
    try
    {
      if (_scheduler != null)
      {
        terminate();
      }
    }
    finally
    {
      super.finalize();
    }
  }

  public void initialize(int number_of_threads)
  {
    if (number_of_threads != deferred)
    {
      if (TBB.USE_ASSERT) assert _scheduler == null : "TaskScheduler already initialized";
      if (TBB.USE_ASSERT)
        assert number_of_threads == -1 || number_of_threads >= 1 : "number_of_threads for TaskScheduler must be -1 or positive";
      _scheduler = super.initializeScheduler(number_of_threads);
    }

  }

  public void terminate()
  {
    // TBB_TRACE(("task_scheduler_init::terminate(): this=%p\n", this));
    Scheduler s = _scheduler;
    _scheduler = null;
    if (TBB.USE_ASSERT) assert s != null : "TaskScheduler.terminate without corresponding TaskScheduler.initialize()";
    super.terminate(s);
  }

  public boolean isActive()
  {
    return _scheduler != null;
  }
}

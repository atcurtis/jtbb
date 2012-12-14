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

import com.xiphis.concurrent.Task;
import com.xiphis.concurrent.TaskGroupContext;

import java.util.logging.Logger;

/**
 * @author atcurtis
 */
public final class TBB
{
  public static final Logger LOG = Logger.getLogger("TBB");

  public static final boolean USE_ASSERT = true;
  public static final boolean USE_DEEP_ASSERT = false;
  public static final boolean USE_THREADING_TOOLS = true;
  public static final boolean RELAXED_OWNERSHIP = false;
  public static final boolean TASK_SCHEDULER_AUTO_INIT = false;
  public static final boolean GATHER_STATISTICS = false;
  public static final boolean SPAWN_OPTIMIZE = true;
  public static final int PAUSE_TIME = 20;
  public static int DefaultNumberOfThreads;

  private static Runnable OneTimeInitialization;

  private TBB()
  {
  }

  public static boolean ConcurrentWaitsEnabled(Task t)
  {
    return (t.prefix()._context._versionAndTraits & TaskGroupContext.concurrent_wait) != 0;
  }

  public static boolean CancellationInfoPresent(Task t)
  {
    return (t.prefix()._context._cancellationRequested.get()) != 0;
  }

  public static boolean AssertOkay(Task task)
  {
    if (TBB.USE_ASSERT) assert task != null;
    // __TBB_ASSERT( (uintptr)&task % task_alignment == 0, "misaligned task"
    // );
    //if (TBB.USE_ASSERT) assert task.state() <= Task.State.recycle;
    // __TBB_ASSERT( (unsigned)task._state()<=(unsigned)task::recycle,
    // "corrupt task (invalid _state)" );
    if (TBB.USE_ASSERT) assert task.prefix()._depth < 1L << 30 : "corrupt task (absurd depth)";
    return true;
  }

  public static void Yield()
  {
    Thread.yield();
  }

  public static void Pause(int millis)
  {
    try
    {
      Thread.sleep(millis);
    }
    catch (InterruptedException e)
    {
      LOG.throwing(TBB.class.getName(), "Pause", e);
      throw new RuntimeException(e);
    }
  }

  public static int DetectNumberOfWorkers()
  {
    return java.lang.Runtime.getRuntime().availableProcessors();
  }

  @SuppressWarnings("deprecation")
  static void rethrow(Throwable ex)
  {
    if (ex != null)
    {
      Thread.currentThread().stop(ex);
    }
  }

  public static void runOneTimeInitialization()
  {
    OneTimeInitialization.run();
  }

  private static void DoOneTimeInitialization()
  {
    Governor.initialize();

    // Now we make sure that no one else runs this initializer
    OneTimeInitialization = new Runnable()
    {
      public void run()
      {
        // do nothing
      }
    };
  }

  static
  {
    OneTimeInitialization = new Runnable()
    {
      public synchronized void run()
      {
        if (OneTimeInitialization == this)
        {
          DoOneTimeInitialization();
        }
      }
    };
  }

}

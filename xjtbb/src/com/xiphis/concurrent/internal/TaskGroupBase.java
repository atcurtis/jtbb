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
import com.xiphis.concurrent.Task;
import com.xiphis.concurrent.TaskGroupContext;

public class TaskGroupBase
{
  protected EmptyTask my_root;
  protected TaskGroupContext my_context;

  protected TaskGroupBase()
  {
    my_context = new TaskGroupContext(TaskGroupContext.bound, TaskGroupContext.default_traits);
    my_root = Task.allocateRoot(Scheduler._emptyTaskFactory, my_context);
    my_root.setRefCount(1);
  }

  ;

  protected TaskGroupBase(int traits)
  {
    my_context = new TaskGroupContext(TaskGroupContext.bound, TaskGroupContext.default_traits | traits);
    my_root = Task.allocateRoot(Scheduler._emptyTaskFactory, my_context);
    my_root.setRefCount(1);
  }

  protected Task owner()
  {
    return TBB.RELAXED_OWNERSHIP ? my_root : Task.currentTask();
  }

  protected final Status internal_run_and_wait(final Runnable f)
  {
    try
    {
      if (!(my_context.isGroupExecutionCancelled()))
      {
        f.run();
      }
    }
    catch (Throwable ex)
    {
      my_context.registerPendingException(ex);
    }
    return await();
  }

  protected final void internal_run(final Runnable f)
  {
    owner().spawn(owner().allocateAdditionalChildOf(my_root, new Factory<RunnableTask>()
    {
      public RunnableTask construct(Object... arguments)
      {
        return new RunnableTask(f);
      }
    }));
  }

  public void run(Runnable f)
  {
    internal_run(f);
  }

  public final Status await()
  {
    try
    {
      owner().prefix()._owner.waitForAll(my_root, null);
    }
    catch (Throwable ex)
    {
      my_context.reset();
      TBB.rethrow(ex);
    }
    if (my_context.isGroupExecutionCancelled())
    {
      my_context.reset();
      return Status.canceled;
    }
    return Status.complete;
  }

  public final boolean isCanceling()
  {
    return my_context.isGroupExecutionCancelled();
  }

  public final void cancel()
  {
    my_context.cancelGroupExecution();
  }

  public static enum Status
  {
    not_complete, complete, canceled
  }

  private static class RunnableTask extends Task
  {
    private final Runnable my_runnable;

    public RunnableTask(Runnable f)
    {
      my_runnable = f;
    }

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      my_runnable.run();
      return null;
    }

  }

}

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
package com.xiphis.concurrent.serial;

import com.xiphis.concurrent.Task;
import com.xiphis.concurrent.internal.Scheduler;
import com.xiphis.concurrent.internal.TBB;
import com.xiphis.concurrent.internal.TaskPrefix;

import java.util.ArrayList;
import java.util.logging.Logger;

public class SimpleScheduler extends Scheduler
{
  private static final Logger LOG = TBB.LOG;

  private static ArrayList<Task> _array = new ArrayList<Task>();

  public SimpleScheduler()
      throws InstantiationException, IllegalAccessException
  {
    super();
  }

  private Task takeTask(int i, int d)
  {
    Task result = null;                /* synchronized(array) */
    {
      do
      {
        if (i > 0 && (result = _array.get(i - 1)) != null)
        {
          TaskPrefix p = result.prefix();
          Task[] p_next = getNextPtr(p);
          _array.set(i - 1, p_next[0]);
          if (p_next[0] == null)
          {
            --i;
          }
          break;
        }
      } while (--i >= d);
      _deepest = i;
    }
    return result;
  }

  @Override
  protected void schedulerSpawn(Task first, Task[] next)
  {
    final Task[] first_ptr = {first};
    Task[] link = first_ptr;
    for (Task t = first_ptr[0]; ; t = link[0])
    {
      if (TBB.USE_ASSERT) assert t.depth() == first.depth() : "tasks must have same depth";
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
          assert ref_count != 0 : "attempt to spawn task whose parent has a ref_count==0 (forgot to set_ref_count?)";
          setRefCountActive(parent.prefix(), true);
        }
      }                        /*
                         * int dst_thread = t.affinity(); if( dst_thread!=0 &&
			 * dst_thread!=affinity() ) { TaskProxy proxy = allocateTask(
			 * TaskProxy.class, null, t.depth(), null ); // Mark as a proxy
			 * proxy.prefix().is_task_proxy = true; proxy.outbox =
			 * &arena->mailbox(dst_thread); proxy.task_and_tag.set(t, 3);
			 * proxy.next_in_mailbox = null; // Link proxy into list where the
			 * original task was. link[0] = proxy; link = proxy.prefix().next;
			 * proxy.prefix().next = t.prefix().next; // Mail the proxy - after
			 * this point, t->prefix().next may be changed by another thread.
			 * proxy.outbox.push(proxy); } else
			 */
      {
        link = getNextPtr(tp);
      }
      if (getNextPtr(tp) == next)
      {
        break;
      }
    }
		/* synchronized (array) */
    {
      int d = first_ptr[0].depth();
      while (d > _array.size())
      {
        _array.add(null);
      }

      link[0] = _array.get(d - 1);
      _array.set(d - 1, first_ptr[0]);

      if (d > _deepest)
      {
        _deepest = d;
      }
    }

    if (TBB.USE_ASSERT) assert assertOkay();

    LOG.exiting(getClass().getName(), "internal_spawn");
  }

  @Override
  protected Task getTask(int d)
  {
    // parallel_reduce has a subtle dependence on the order in which tasks are
    // selected from the deque for execution. If the task selected is not the
    // next immediate task -- i.e. there is a hole between the previous task and
    // the task selected -- then the selected task must be treated as stolen and
    // the body of the task split in order to ensure the correctness of the join
    // operations.
    for (Task result = null; ; )
    {
      if (_deepest >= d)
      {
        result = takeTask(_deepest, d);
      }
      if (result != null)
      {
        if (isProxy(result))
        {
          result = stripProxy(result);
          if (result == null)
          {
            continue;
          }
          if (TBB.GATHER_STATISTICS)
          {
            ++_statistics.proxy_execute_count;
          }
          // Task affinity has changed.
          setRunningTask(result);
          result.noteAffinity(affinity());
        }
      }
      return result;
    }
  }

  @Override
  public boolean assertOkay()
  {
    return isRegistered();
  }

  @Override
  protected void propagateWorkerCancellation()
  {
    // No workers, nothing to do.
  }

  @Override
  protected void syncWorkerCancellation(int cancel_count)
  {
    // No workers, nothing to do.
  }

}

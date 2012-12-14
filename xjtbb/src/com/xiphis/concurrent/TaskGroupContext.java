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
import com.xiphis.concurrent.internal.ListNode;
import com.xiphis.concurrent.internal.Scheduler;
import com.xiphis.concurrent.internal.TBB;

import java.util.concurrent.atomic.AtomicInteger;

public final class TaskGroupContext extends Scheduler.TaskGroupContextBase
{

  public static final Kind isolated = Kind.isolated;

  ;
  public static final Kind bound = Kind.bound;
  public static final Kind binding_required = Kind.bound;
  public static final Kind binding_completed = Kind.bound_done;
  public static final int exact_exception = 0x0001;
  public static final int no_cancellation = 0x0002;
  public static final int concurrent_wait = 0x0004;
  public static final int default_traits = exact_exception;
  /**
   * Specifies whether cancellation was request for this task group.
   */
  public final AtomicInteger _cancellationRequested = new AtomicInteger(0);
  /**
   * Used to form the thread specific list of contexts without additional
   * memory allocation. <br>
   * A context is included into the list of the current thread when its
   * binding to its parent happens. Any context can be present in the list of
   * one thread only.
   */
  final ListNode<TaskGroupContext> _node = new ListNode<TaskGroupContext>(this);
  /**
   * Version for run-time checks and behavioural traits of the context.<br>
   * Version occupies low 16 bits, and traits (zero or more ORed enumerators
   * from the traits_type enumerations) take the next 16 bits. Original
   * (zeroth) version of the context did not support any traits.
   */
  public int _versionAndTraits;
  /**
   * Pointer to the container storing exception being propagated across this
   * task group.
   */
  public Throwable _exception;
  Kind _kind;
  /**
   * Pointer to the context of the parent cancellation group. NULL for
   * isolated contexts.
   */
  TaskGroupContext _parent;
  /**
   * Scheduler that registered this context in its thread specific list.
   */
  Scheduler _owner;

  /**
   * Default & binding constructor.
   * <p/>
   * <p/>
   * By default a bound context is created. That is this context will be bound
   * (as child) to the context of the task calling
   * task::allocate_root(this_context) method. Cancellation requests passed to
   * the parent context are propagated to all the contexts bound to it.
   * <p/>
   * <p/>
   * If task_group_context::isolated is used as the argument, then the tasks
   * associated with this context will never be affected by events in any
   * other context.
   * <p/>
   * <p/>
   * Creating isolated contexts involve much less overhead, but they have
   * limited utility. Normally when an exception occurs in an algorithm that
   * has nested ones running, it is desirably to have all the nested
   * algorithms cancelled as well. Such a behaviour requires nested algorithms
   * to use bound contexts.
   * <p/>
   * <p/>
   * There is one good place where using isolated algorithms is beneficial. It
   * is a master thread. That is if a particular algorithm is invoked directly
   * from the master thread (not from a TBB task), supplying it with
   * explicitly created isolated context will result in a faster algorithm
   * startup.
   * <p/>
   * <p/>
   * VERSIONING NOTE: <br>
   * Implementation(s) of task_group_context constructor(s) cannot be made
   * entirely out-of-line because the run-time version must be set by the user
   * code. This will become critically important for binary compatibility, if
   * we ever have to change the size of the context object.
   * <p/>
   * <p/>
   * Boosting the runtime version will also be necessary whenever new fields
   * are introduced in the currently unused padding areas or the meaning of
   * the existing fields is changed or extended.
   */
  TaskGroupContext()
  {
    init(Kind.bound, default_traits);
  }

  public TaskGroupContext(Kind relation_with_parent, int traits)
  {
    init(relation_with_parent, traits);
  }

  private void init(Kind relation_with_parent, int traits)
  {
    _kind = relation_with_parent;
    _versionAndTraits = traits;

    if (TBB.USE_ASSERT) assert _kind == isolated || _kind == bound : "Context can be created only as isolated or bound";

    _parent = null;
    _exception = null;
    if (_kind == Kind.bound)
    {
      Scheduler s;
      if (TBB.TASK_SCHEDULER_AUTO_INIT)
      {
        s = Governor.localSchedulerWithAutoInit();
      }
      else
      {
        s = Governor.localScheduler();
      }
      _owner = s;
      if (TBB.USE_ASSERT) assert _owner != null : "Thread has not activated a task_scheduler_init object?";

      super.init_node(s, _node);
    }
  }

  public final boolean cancelGroupExecution()
  {
    if (TBB.USE_ASSERT)
      assert _cancellationRequested.get() == 0 || _cancellationRequested.get() == 1 : "Invalid cancellation state";

    if (_cancellationRequested.get() != 0 || !_cancellationRequested.compareAndSet(0, 1))
    {
      // This task group has already been cancelled
      return false;
    }
    Governor.localScheduler().propagateCancellation(this);
    return true;
  }

  public final boolean isGroupExecutionCancelled()
  {
    return _cancellationRequested.get() != 0;
  }

  // IMPORTANT: It is assumed that this method is not used concurrently!
  public void reset()
  {
    // ! TODO Add assertion that this context does not have children
    // No fences are necessary since this context can be accessed from
    // another thread
    // only after stealing happened (which means necessary fences were
    // used).
    _exception = null;
    _cancellationRequested.set(0);
  }

  public final void propagateCancellationFromAncestors()
  {
    TaskGroupContext parent = _parent;
    while (parent != null && parent._cancellationRequested.get() == 0)
    {
      parent = parent._parent;
    }
    if (parent != null)
    {
      // One of our ancestor groups was cancelled. Cancel all its
      // descendants.
      TaskGroupContext ctx = this;
      do
      {
        ctx._cancellationRequested.set(1);
        ctx = ctx._parent;
      } while (ctx != parent);
    }
  }

  public final void registerPendingException(Throwable ex)
  {
    if (_cancellationRequested.get() != 0)
    {
      return;
    }
    if (ex != null && cancelGroupExecution())
    {
      _exception = ex;
    }
  }

  public boolean isAlive()
  {
    return this == _node._link;
  }

  public static enum Kind
  {
    isolated, bound, bound_done,
  }
}

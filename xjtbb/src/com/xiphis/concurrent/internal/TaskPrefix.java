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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class TaskPrefix
{
  private static final Logger LOG = TBB.LOG;

  /**
   * In the "continuation-passing style" of programming, this field is the
   * difference of the number of allocated children minus the number of
   * children that have completed. In the "blocking style" of programming,
   * this field is one more than the difference.
   */
  final AtomicInteger _refCount = new AtomicInteger();
  /**
   * "next" field for list of task
   */
  final Task[] _next = new Task[1];

  // ! Shared _context that is used to communicate asynchronous _state
  // changes
  Pair<Task, TaskPrefix> _link;
  /**
   * Currently it is used to broadcast cancellation requests generated both by
   * users and as the result of unhandled exceptions in the task::execute()
   * methods.
   */
  TaskGroupContext _context;

  // ! The task whose reference count includes me.
  /**
   * The scheduler that owns the task.
   */
  Scheduler _owner;

  // ! Reference count used for synchronization.
  /**
   * In the "blocking style" of programming, this field points to the _parent
   * task. In the "continuation-passing style" of programming, this field
   * points to the continuation of the _parent.
   */
  Task _parent;
  /**
   * Scheduling depth
   */
  int _depth;
  /**
   * This _state is exposed to users via method task::_state().
   */
  Task.State _state;

  // ! Miscellaneous _state that is not directly visible to users, stored
  // as a
  // byte for compactness.
  /**
   * 0x0 -> version 1.0 task 0x1 -> version 3.0 task 0x2 -> task_proxy 0x40 ->
   * task has live _refCount
   */
  // unsigned char extra_state;
  boolean _taskProxy;
  boolean _refCountActive;
  int _affinity;

  TaskPrefix(Pair<Task, TaskPrefix> link)
  {
    if (TBB.USE_ASSERT) assert link.second == null;
    _link = link;
    _link.second = this;
  }

  /**
   * The task corresponding to this task_prefix.
   *
   * @return
   */
  public Task task()
  {
    return _link.first;
  }
}

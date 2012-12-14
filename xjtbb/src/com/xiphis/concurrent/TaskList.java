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

public final class TaskList extends Scheduler.TaskListBase
{
  final Task[] first;
  Task[] next_ptr;

  /**
   * Construct empty list
   */
  public TaskList()
  {
    first = new Task[1];
    next_ptr = first;
  }

  /**
   * True if list if empty; false otherwise.
   *
   * @return
   */
  public boolean empty()
  {
    return first[0] == null;
  }

  /**
   * Push task onto back of list.
   *
   * @param task
   */
  public void push_back(Task task)
  {
    super.push_back(next_ptr, task);
  }

  /**
   * Pop the front task from the list.
   *
   * @return
   */
  public Task pop_front()
  {
    if (TBB.USE_ASSERT) assert !empty() : "attempt to pop item from empty task_list";
    return super.pop_front(first, next_ptr);
  }

  /**
   * Clear the list
   */
  public void clear()
  {
    first[0] = null;
    next_ptr = first;
  }
}

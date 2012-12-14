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

import java.util.concurrent.atomic.AtomicStampedReference;

public final class TaskProxy extends Task
{
  static final int pool_bit = 1;
  static final int mailbox_bit = 2;
  final AtomicStampedReference<Task> _taskAndTag;
  // ! Pointer to next task_proxy in a mailbox
  TaskProxy _nextInMailbox;
  // ! Mailbox to which this was mailed.
  MailOutbox _outbox;

  TaskProxy(AtomicStampedReference<Task> task_and_tag)
  {
    this._taskAndTag = task_and_tag;
  }

  public TaskProxy(Task task, int tag)
  {
    _taskAndTag = new AtomicStampedReference<Task>(task, tag);
  }

  /*
   * (non-Javadoc)
   *
   * @see com.google.common.tasks.Task#execute()
   */
  @Override
  public Task execute()
  {
    throw new IllegalStateException("TaskProxy should not be executed");
  }

  public boolean recipientIsIdle()
  {
    return _outbox.recipientIsIdle();
  }

  public int getTag()
  {
    return _taskAndTag.getStamp();
  }
}

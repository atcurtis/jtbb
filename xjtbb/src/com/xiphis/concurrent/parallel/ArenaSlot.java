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
package com.xiphis.concurrent.parallel;

import com.xiphis.concurrent.Task;

import java.util.concurrent.atomic.AtomicReference;

final class ArenaSlot
{
  public static final Task[] EMPTY_TASK_POOL = null;
  public static final Task[] LOCKED_TASK_POOL = new Task[0];
  /**
   * Task pool (the deque of task pointers) of the scheduler that owns this
   * _slot.
   * <p/>
   * Also is used to specify if the _slot is empty or locked: 0 - empty -1 -
   * locked
   */
  final AtomicReference<Task[]> _taskPool = new AtomicReference<Task[]>();
  /**
   * Index of the first ready task in the deque.
   * <p/>
   * Modified by thieves, and by the owner during compaction/reallocation
   */
  volatile int head;
  /**
   * Index of the element following the last ready task in the deque.
   * <p/>
   * Modified by the owner thread.
   */
  volatile int tail;

}

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

import java.util.concurrent.atomic.AtomicMarkableReference;


public final class MailOutbox
{
  /**
   * Pointer to last task_proxy in mailbox, or NULL if box is empty.
   * <p/>
   * Mark represents lock on the box.
   */
  protected final AtomicMarkableReference<TaskProxy> _last;
  // ! Pointer to first task_proxy in mailbox, or NULL if box is empty.
  protected TaskProxy _first;
  /**
   * Owner of mailbox is not executing a task, and has drained its own task
   * pool.
   */
  volatile boolean _is_idle;

  public MailOutbox()
  {
    _first = null;
    _last = new AtomicMarkableReference<TaskProxy>(null, false);
    _is_idle = false;
  }

  /**
   * Acquire lock on the box.
   *
   * @return
   */
  TaskProxy acquire()
  {
    for (boolean[] mark = new boolean[1]; ; )
    {
      TaskProxy last = _last.get(mark);
      if (!mark[0] && _last.weakCompareAndSet(last, last, false, true))
      {
        if (TBB.USE_ASSERT) assert (_first == null) == (last == null);
        return last;
      }
      TBB.Yield();
    }
  }

  TaskProxy internalPop()
  {
    // ! No fence on load of _first, because if it is NULL, there's
    // nothing further to read from another thread.
    TaskProxy result = _first;
    if (result != null)
    {
      TaskProxy f = result._nextInMailbox;
      if (f != null)
      {
        // No lock required
        _first = f;
      }
      else
      {
        // acquire() has the necessary fence.
        TaskProxy l = acquire();
        if (TBB.USE_ASSERT) assert result == _first;
        if ((_first = result._nextInMailbox) == null)
        {
          l = null;
        }
        _last.set(l, false);
      }
    }
    return result;
  }

  /**
   * Push task_proxy onto the mailbox queue of another thread.
   */
  public void push(TaskProxy t)
  {
    if (TBB.USE_ASSERT) assert t != null;
    t._nextInMailbox = null;
    TaskProxy l = acquire();
    if (l != null)
    {
      l._nextInMailbox = t;
    }
    else
    {
      _first = t;
    }
    // Fence required because caller is sending the task_proxy to another
    // thread.
    _last.set(t, false);
  }

  /**
   * Verify that this is initialized empty mailbox.
   * <p/>
   * Raise assertion if this is not in initialized _state.
   */
  public void assertIsInitialized()
  {
    if (TBB.USE_ASSERT)
    {
      assert _first == null;
      assert _last.getReference() == null;
      assert !_is_idle;
    }
  }

  /**
   * Drain the mailbox
   *
   * @return
   */
  public int drain()
  {
    int k = 0;
    // No fences here because other threads have already quit.
    for (TaskProxy t; (t = _first) != null; ++k)
    {
      _first = t._nextInMailbox;
    }
    return k;
  }

  /**
   * True if thread that owns this mailbox is looking for work.
   *
   * @return
   */
  public boolean recipientIsIdle()
  {
    return _is_idle;
  }
}

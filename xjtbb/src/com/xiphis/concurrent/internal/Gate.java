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

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class Gate<T>
{
  private static final Logger LOG = TBB.LOG;

  private final T _groundState;
  private final AtomicReference<T> _state;
  private final Object _cond = new Object();

  public Gate(T groundState)
  {
    _groundState = groundState;
    _state = new AtomicReference<T>(groundState);
  }

  public T getState()
  {
    return _state.get();
  }

  public void await()
  {
    while (_state.get() == _groundState)
    {
      TBB.Yield();
      synchronized (_cond)
      {
        while (_state.get() == _groundState)
        {
          try
          {
            _cond.wait(500);
          }
          catch (InterruptedException e)
          {
            // do nothing
          }
        }
      }
    }
  }

  /**
   * Update _state=value if _state==comparand
   *
   * @param value
   * @param comparand
   */
  public void tryUpdate(T value, T comparand)
  {
    tryUpdate(value, comparand, false);
  }

  /**
   * Update _state=value if _state==comparand (flip==false) or _state!=comparand
   * (flip==true)
   *
   * @param value
   * @param comparand
   * @param flip
   */
  public void tryUpdate(T value, T comparand, boolean flip)
  {
    if (TBB.USE_ASSERT)
      assert comparand != _groundState || value != _groundState : "either value or comparand must be non-zero";
    retry:
    for (; ; )
    {
      T old_state = _state.get();
      // First test for condition without using atomic operation
      if (flip ? old_state != comparand : old_state == comparand)
      {
        // Now atomically retest condition and set.
        if (_state.compareAndSet(old_state, value))
        {
          if (value != _groundState)
          {
            synchronized (_cond)
            {
              _cond.notifyAll();
            }
          }
        }
        else
        {
          continue retry;
        }
      }
      return;
    }
  }
}

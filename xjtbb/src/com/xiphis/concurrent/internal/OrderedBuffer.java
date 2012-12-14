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

import com.xiphis.concurrent.Filter;

//! A buffer of input items for a filter.

/**
 * Each item is a task_info, inserted into a position in the buffer
 * corresponding to a Token.
 */
public final class OrderedBuffer<T>
{

  /**
   * Initial size for "array"
   * <p/>
   * Must be a power of 2
   */
  static final int _initialBufferSize = 4;

  ;
  /**
   * Serializes updates.
   */
  private final Object _arrayMutex = new Object();
  /**
   * Array of deferred tasks that cannot yet start executing.
   */
  TaskInfo<T>[] _array;

  /**
   * Size of array
   * <p/>
   * Always 0 or a power of 2
   */
  int _arraySize;

  /**
   * Lowest token that can start executing.
   * <p/>
   * All prior Token have already been seen.
   */
  int _lowToken;

  /**
   * Used only for out of order buffer.
   */
  int _highToken;

  /**
   * True for ordered filter, false otherwise.
   */
  boolean _ordered;

  /**
   * True for thread-bound filter, false otherwise.
   */
  boolean _bound;

  /**
   * Construct empty buffer.
   *
   * @param is_ordered
   * @param is_bound
   */
  public OrderedBuffer(boolean is_ordered, boolean is_bound)
  {
    _ordered = is_ordered;
    _bound = is_bound;
    grow(_initialBufferSize);
  }

  /**
   * Caller is responsible to acquiring a lock on "_arrayMutex".
   */
  @SuppressWarnings("unchecked")
  private void grow(int minimum_size)
  {
    int old_size = _arraySize;
    int new_size = old_size != 0 ? 2 * old_size : _initialBufferSize;
    while (new_size < minimum_size)
    {
      new_size *= 2;
    }
    TaskInfo<T>[] new_array = new TaskInfo[new_size];
    TaskInfo<T>[] old_array = _array;

    if (old_array != null)
    {
      int t = _lowToken;
      for (int i = 0; i < old_size; ++i, ++t)
      {
        new_array[t & new_size - 1] = new TaskInfo<T>(old_array[t & old_size - 1]);
      }
    }
    else
    {
      for (int i = 0; i < new_size; ++i)
      {
        new_array[i] = new TaskInfo<T>();
      }
    }
    _array = new_array;
    _arraySize = new_size;
  }

  /**
   * Put a token into the buffer.
   * <p/>
   * If task information was placed into buffer, returns true; otherwise
   * returns false, informing the caller to create and spawn a task.
   *
   * @param putter
   * @return
   */
  public boolean putToken(StageTask<T> putter)
  {
    synchronized (_arrayMutex)
    {
      int token;
      if (_ordered)
      {
        if (!putter.isTokenReady())
        {
          putter.setToken(_highToken++);
          putter.setTokenReady(true);
        }
        token = putter.getToken();
      }
      else
      {
        token = _highToken++;
      }
      if (TBB.USE_ASSERT) assert (token - _lowToken) >= 0;
      if (token != _lowToken || _bound)
      {
        // Trying to put token that is beyond _lowToken.
        // Need to wait until _lowToken catches up before dispatching.
        if (token - _lowToken >= _arraySize)
        {
          grow(token - _lowToken + 1);
        }
        // ITT_NOTIFY(sync_releasing, this);
        putter.putTaskInfo(_array[token & _arraySize - 1]);
        return true;
      }
    }
    return false;
  }

  /**
   * Note that processing of a token is finished.
   * <p/>
   * Fires up processing of the next token, if processing was deferred.
   *
   * @param token
   * @param spawner
   */
  public void noteDone(int token, StageTask<T> spawner)
  {
    TaskInfo<T> wakee = new TaskInfo<T>();
    synchronized (_arrayMutex)
    {
      if (!_ordered || token == _lowToken)
      {
        // Wake the next task
        TaskInfo<T> item = _array[++_lowToken & _arraySize - 1];
        // ITT_NOTIFY( sync_acquired, this );
        wakee = item;
        item._valid = false;
      }
    }
    if (wakee._valid)
    {
      spawner.spawnStageTask(wakee);
    }
  }

  /**
   * The method destroys all data in filters to prevent memory leaks
   *
   * @param my_filter
   */
  public void clear(Filter<T> my_filter)
  {
    int t = _lowToken;
    for (int i = 0; i < _arraySize; ++i, ++t)
    {
      TaskInfo<T> temp = _array[t & _arraySize - 1];
      if (temp._valid)
      {
        my_filter.finalize(temp._object);
        temp._valid = false;
      }
    }
  }

  public boolean returnItem(TaskInfo<T> info, boolean advance)
  {
    synchronized (_arrayMutex)
    {
      TaskInfo<T> item = _array[_lowToken & _arraySize - 1];
      // ITT_NOTIFY( sync_acquired, this );
      if (item._valid)
      {
        info = item;
        item._valid = false;
        if (advance)
        {
          _lowToken++;
        }
        return true;
      }
      return false;
    }
  }

  public void putItem(TaskInfo<T> info)
  {
    info._valid = true;
    synchronized (_arrayMutex)
    {
      int token;
      if (_ordered)
      {
        if (!info._tokenReady)
        {
          info._token = _highToken++;
          info._tokenReady = true;
        }
        token = info._token;
      }
      else
      {
        token = _highToken++;
      }
      if (TBB.USE_ASSERT) assert (token - _lowToken) >= 0;
      if (token - _lowToken >= _arraySize)
      {
        grow(token - _lowToken + 1);
      }
      // ITT_NOTIFY( sync_releasing, this );
      _array[token & _arraySize - 1] = info;
    }
  }

  public int getLowToken()
  {
    return _lowToken;
  }

  /**
   * This structure is used to store task information in a input buffer
   *
   * @param <T>
   * @author atcurtis
   */
  public static class TaskInfo<T>
  {
    public T _object;
    /**
     * Invalid unless a task went through an ordered stage.
     */
    public int /* Token */ _token;
    /**
     * False until _token is set.
     */
    public boolean _tokenReady;
    public boolean _valid;

    public TaskInfo()
    {
    }

    public TaskInfo(TaskInfo<T> src)
    {
      assign(src);
    }

    public void assign(TaskInfo<T> src)
    {
      _object = src._object;
      _token = src._token;
      _tokenReady = src._tokenReady;
      _valid = src._valid;
    }
  }
};

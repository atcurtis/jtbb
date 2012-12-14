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

import java.util.logging.Logger;

public class ParallelWhile<T>
{
  private static final Logger LOG = TBB.LOG;

  private final Factory<WhileGroupTask<T>> _whileGroupTaskFactory;
  private Body<T> _body;
  private EmptyTask _barrier;

  /**
   *
   */
  public ParallelWhile()
  {
    _whileGroupTaskFactory = new Factory<WhileGroupTask<T>>()
    {
      public WhileGroupTask<T> construct(Object... arguments)
      {
        return new WhileGroupTask<T>(_body);
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static <T> T[] newArray(int size)
  {
    return (T[]) new Object[size];
  }

  /**
   * @param stream
   * @param body
   */
  public void run(final Stream<T> stream, Body<T> body)
  {
    EmptyTask barrier = Task.allocateRoot(Scheduler._emptyTaskFactory);
    _body = body;
    _barrier = barrier;
    _barrier.setRefCount(2);
    WhileTask<T> w = _barrier.allocateChild(new Factory<WhileTask<T>>()
    {
      public WhileTask<T> construct(Object... arguments)
      {
        return new WhileTask<T>(stream, _whileGroupTaskFactory, _barrier);
      }
    });
    _barrier.spawnAndWaitForAll(w);
    _barrier.destroy(_barrier);
    _barrier = null;
    _body = null;

  }

  /**
   * @param item
   */
  public void add(final T item)
  {
    if (TBB.USE_ASSERT) assert _barrier != null : "attempt to add to parallel_while that is not running";
    Task t = Task.currentTask();
    IterationTask<T> i = t.allocateAdditionalChildOf(_barrier, new Factory<IterationTask<T>>()
    {
      public IterationTask<T> construct(Object... arguments)
      {
        return new IterationTask<T>(item, _body);
      }
    });
    t.spawn(i);
  }

  /**
   * @param <T>
   * @author atcurtis
   */
  public interface Body<T>
  {
    /**
     * @param item
     */
    void apply(T item);
  }

  /**
   * @param <T>
   * @author atcurtis
   */
  public interface Stream<T>
  {
    /**
     * @param item
     * @param index
     * @return
     */
    boolean popIfPresent(T[] item);
  }

  private static class IterationTask<T> extends Task
  {
    private final Body<T> my_body;
    private final T my_value;

    public IterationTask(T item, Body<T> body)
    {
      my_body = body;
      my_value = item;
    }

    @Override
    public Task execute()
    {
      my_body.apply(my_value);
      return null;
    }
  }

  private static class WhileGroupTask<T> extends Task
  {
    public static final int max_arg_size = 4;
    public final T[] my_arg;
    private final Body<T> my_body;
    public int size;
    private int idx;

    public WhileGroupTask(Body<T> body)
    {
      my_body = body;
      my_arg = newArray(max_arg_size);
    }

    @Override
    public Task execute()
    {
      if (TBB.USE_ASSERT) assert size > 0;
      TaskList list = new TaskList();
      Task t;
      idx = 0;
      for (Factory<IterationTask<T>> iterationTaskFactory = new Factory<IterationTask<T>>()
      {
        public IterationTask<T> construct(Object... arguments)
        {
          // TODO Auto-generated method stub
          return new IterationTask<T>(my_arg[idx], my_body);
        }
      }; ; )
      {
        t = allocateChild(iterationTaskFactory);
        if (++idx == size)
        {
          break;
        }
        list.push_back(t);
      }
      setRefCount(idx + 1);
      spawn(list);
      spawnAndWaitForAll(t);
      return null;
    }
  }

  private static class WhileTask<T> extends Task
  {
    private final Stream<T> my_stream;
    private final EmptyTask my_barrier;
    private final Factory<WhileGroupTask<T>> whileGroupTaskFactory;
    private final T[] my_arg;

    public WhileTask(Stream<T> stream, Factory<WhileGroupTask<T>> factory, EmptyTask barrier)
    {
      my_stream = stream;
      whileGroupTaskFactory = factory;
      my_barrier = barrier;
      my_arg = newArray(1);
    }

    @Override
    public Task execute()
    {
      WhileGroupTask<T> t = allocateAdditionalChildOf(my_barrier, whileGroupTaskFactory);
      int k = 0;
      while (my_stream.popIfPresent(my_arg))
      {
        t.my_arg[k] = my_arg[0];
        if (++k == WhileGroupTask.max_arg_size)
        {
          // There might be more iterations.
          recycleToReexecute();
          break;
        }
      }
      if (k == 0)
      {
        destroy(t);
        return null;
      }
      else
      {
        t.size = k;
        return t;
      }
    }
  }
}

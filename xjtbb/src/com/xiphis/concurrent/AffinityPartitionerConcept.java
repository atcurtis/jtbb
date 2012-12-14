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

public class AffinityPartitionerConcept<R extends RangeConcept<R>.Range>
    extends PartitionerConcept<R, AffinityPartitionerConcept<R>.AffinityPartitioner>
{

  /**
   * Array that remembers affinities of tree positions to affinity_id.
   * <p/>
   * NULL if my_size==0.
   */
  private int[] my_array;

  public static <R extends RangeConcept<R>.Range> //
  AffinityPartitionerConcept<R>.AffinityPartitioner newInstance()
  {
    return new AffinityPartitionerConcept<R>().clone(null);
  }

  /**
   * Resize my_array.
   * <p/>
   * Retains values if resulting size is the same.
   */
  private void resize(int factor)
  {
    // Check factor to avoid asking for number of workers while there might
    // be no arena.
    int new_size = factor != 0 ? factor * (Governor.numberOfWorkersInArena() + 1) : 0;
    if (my_array == null || new_size != my_array.length)
    {
      if (my_array != null)
      {
        my_array = null;
      }
      if (new_size != 0)
      {
        my_array = new int[new_size];
      }
    }
  }

  @Override
  public AffinityPartitioner clone(AffinityPartitioner partitioner)
  {
    // TODO Auto-generated method stub
    return new AffinityPartitioner();
  }

  @Override
  public AffinityPartitioner split(AffinityPartitioner partitioner)
  {
    return new AffinityPartitioner(partitioner);
  }

  public class AffinityPartitioner
      extends PartitionerConcept<R, AffinityPartitionerConcept<R>.AffinityPartitioner>.Partitioner
  {
    // ! Must be power of two
    static final int factor = 16;
    static final int VICTIM_CHUNKS = 4;
    TaskList delay_list;
    int map_begin, map_end;
    int num_chunks;

    AffinityPartitioner()
    {
      // __TBB_ASSERT( (factor&(factor-1))==0,
      // "factor must be power of two" );
      resize(factor);
      map_begin = 0;
      map_end = my_array.length;
      // num_chunks = internal::get_initial_auto_partitioner_divisor();
    }

    AffinityPartitioner(AffinityPartitioner p)
    {
      // __TBB_ASSERT( p.map_end-p.map_begin<factor ||
      // (p.map_end-p.map_begin)%factor==0, NULL );
      num_chunks = p.num_chunks /= 2;
      int e = p.map_end;
      int d = (e - p.map_begin) / 2;
      if (d > factor)
      {
        d &= (0 - factor);
      }
      map_end = e;
      map_begin = p.map_end = e - d;
    }

    @Override
    public boolean shouldExecuteRange(R range, Task t)
    {
      if (num_chunks < VICTIM_CHUNKS && t.isStolenTask())
      {
        num_chunks = VICTIM_CHUNKS;
      }
      return num_chunks == 1;
    }

    @Override
    public Task continueAfterExecuteRange(Task t)
    {
      Task first = null;
      if (!delay_list.empty())
      {
        first = delay_list.pop_front();
        while (!delay_list.empty())
        {
          t.spawn(first);
          first = delay_list.pop_front();
        }
      }
      return first;
    }

    @Override
    public boolean decideWhetherToDelay()
    {
      // The possible underflow caused by "-1u" is deliberate
      return (map_begin & (factor - 1)) == 0 && map_end - map_begin - 1 < factor;
    }

    @Override
    public void setAffinity(Task t)
    {
      if (map_begin < map_end)
      {
        t.setAffinity(my_array[map_begin]);
      }
    }

    @Override
    public void noteAffinity(int id)
    {
      if (map_begin < map_end)
      {
        my_array[map_begin] = id;
      }
    }

    @Override
    public void spawnOrDelay(boolean delay, Task a, Task b)
    {
      if (delay)
      {
        delay_list.push_back(b);
      }
      else
      {
        a.spawn(b);
      }
    }

    @Override
    protected void finalize()
        throws Throwable
    {
      try
      {
        // The delay_list can be non-empty if an exception is thrown.
        while (!delay_list.empty())
        {
          Task t = delay_list.pop_front();
          t.destroy(t);
        }
      }
      finally
      {
        super.finalize();
      }
    }
  }
}

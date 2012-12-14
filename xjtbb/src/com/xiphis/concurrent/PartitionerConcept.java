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

public abstract class PartitionerConcept<R extends RangeConcept<R>.Range, P extends PartitionerConcept<R, P>.Partitioner>
{

  /**
   * Clone partitioner.
   *
   * @param partitioner
   * @return
   */
  public abstract P clone(P partitioner);

  /**
   * Splits partitioner into to partitioners.
   *
   * @param partitioner
   * @return
   */
  public abstract P split(P partitioner);

  public abstract class Partitioner
  {
    /**
     * Test if range should be passed to the body of task.
     *
     * @param range
     * @param task
     * @return false if range should instead be split.
     */
    public boolean shouldExecuteRange(R range, Task task)
    {
      return false;
    }

    public Task continueAfterExecuteRange(Task t)
    {
      return null;
    }

    public boolean decideWhetherToDelay()
    {
      return false;
    }

    public void setAffinity(Task t)
    {
    }

    public void noteAffinity(int id)
    {
    }

    public void spawnOrDelay(boolean delay, Task a, Task b)
    {
      a.spawn(b);
    }

    public final PartitionerConcept<R, P> concept()
    {
      return PartitionerConcept.this;
    }
  }
}

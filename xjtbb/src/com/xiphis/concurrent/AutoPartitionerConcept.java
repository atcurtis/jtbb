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

public class AutoPartitionerConcept<R extends RangeConcept<R>.Range>
    extends PartitionerConcept<R, AutoPartitionerConcept<R>.AutoPartitioner>
{

  static final int X_FACTOR = 4;
  static final int VICTIM_CHUNKS = 4;

  public static int getInitialAutoPartitionerDivisor()
  {
    return X_FACTOR * (Governor.numberOfWorkersInArena() + 1);

  }

  @Override
  public AutoPartitioner clone(AutoPartitioner partitioner)
  {
    return new AutoPartitioner(getInitialAutoPartitionerDivisor());
  }

  @Override
  public AutoPartitioner split(AutoPartitioner partitioner)
  {
    return new AutoPartitioner(partitioner.num_chunks / 2);
  }

  public class AutoPartitioner
      extends PartitionerConcept<R, AutoPartitionerConcept<R>.AutoPartitioner>.Partitioner
  {
    private int num_chunks;

    AutoPartitioner(int num_chunks)
    {
      this.num_chunks = num_chunks;
    }

    public boolean shouldExecuteRange(Task t)
    {
      if (num_chunks < VICTIM_CHUNKS && t.isStolenTask())
      {
        num_chunks = VICTIM_CHUNKS;
      }
      return num_chunks == 1;
    }
  }
}

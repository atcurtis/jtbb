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

import com.xiphis.concurrent.internal.TBB;

public abstract class BlockedRangeConcept<Value, R extends BlockedRangeConcept<Value, R>.BlockedRange> //
    extends RangeConcept<R>
{
  private final int _grainsize;

  public BlockedRangeConcept()
  {
    this(1);
  }

  public BlockedRangeConcept(int grainsize)
  {
    if (TBB.USE_ASSERT) assert grainsize > 0 : "grainsize must be positive";
    _grainsize = grainsize;
  }

  public abstract R newInstance(Value begin, Value end);

  @Override
  public R clone(R range)
  {
    return newInstance(range.begin(), range.end());
  }

  @Override
  public R split(R r)
  {
    if (TBB.USE_ASSERT) assert r.isDivisible() : "cannot split indivisible range";
    R s = clone(r);
    Value middle = r.increment(r.begin(), r.difference(r.end(), r.begin()) / 2);
    r._end = middle;
    s._begin = middle;
    return s;
  }

  public abstract class BlockedRange extends Range
  {
    private Value _begin;
    private Value _end;

    protected BlockedRange(Value begin, Value end)
    {
      _begin = begin;
      _end = end;
    }

    public final Value begin()
    {
      return _begin;
    }

    public final Value end()
    {
      return _end;
    }

    /**
     * Compares values i and j.
     *
     * @param i
     * @param j
     * @return true if value i precedes value j.
     */
    public abstract boolean lessThan(Value i, Value j);

    /**
     * Number of values in range i..j
     *
     * @param i
     * @param j
     * @return
     */
    public abstract int difference(Value i, Value j);

    /**
     * @param i
     * @param k
     * @return kth value after i
     */
    public abstract Value increment(Value i, int k);
  }
}

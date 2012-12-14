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

import java.util.Iterator;
import java.util.List;

public class ListRangeConcept<T>
    extends RangeConcept<ListRangeConcept<T>.ListRange>
{

  private final int _grainsize;

  public ListRangeConcept()
  {
    _grainsize = 1;
  }

  public ListRangeConcept(int grainsize)
  {
    _grainsize = grainsize;
  }

  public static <T> //
  ListRangeConcept<T>.ListRange newInstance(List<T> list)
  {
    return new ListRangeConcept<T>().newRange(list);
  }

  public ListRange newRange(List<T> list)
  {
    return new ListRange(list);
  }

  /**
   * Clone range.
   *
   * @param range
   * @return
   */
  @Override
  public ListRange clone(ListRange range)
  {
    return new ListRange(range._list.subList(0, range.size()));
  }

  /**
   * Split range into two subranges.
   *
   * @param range
   * @return
   */
  @Override
  public ListRange split(ListRange range)
  {
    int split = range.isDivisible() ? (range.size() + 1) / 2 : range.size();
    ListRange result = new ListRange(range._list.subList(split, range.size()));
    range._list = range._list.subList(0, split);
    return result;
  }

  public class ListRange
      extends RangeConcept<ListRangeConcept<T>.ListRange>.Range
  {
    List<T> _list;

    ListRange(List<T> list)
    {
      _list = list;
    }

    List<T> list()
    {
      return _list;
    }

    Iterator<T> iterator()
    {
      return _list.iterator();
    }

    // ! The grain size for this range.
    int grainsize()
    {
      return _grainsize;
    }

    @Override
    public int size()
    {
      return _list.size();
    }

    @Override
    public boolean isEmpty()
    {
      return _list.isEmpty();
    }

    @Override
    public boolean isDivisible()
    {
      // TODO Auto-generated method stub
      return grainsize() < size();
    }
  }
}

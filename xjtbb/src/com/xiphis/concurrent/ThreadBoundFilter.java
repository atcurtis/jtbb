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

import com.xiphis.concurrent.internal.OrderedBuffer;
import com.xiphis.concurrent.internal.TBB;

public abstract class ThreadBoundFilter<T> extends Filter<T>
{
  protected ThreadBoundFilter(Mode filter_mode)
  {
    super(filter_mode.my_bits | Filter.filter_is_bound);
  }

  ;

  /**
   * If a data item is available, invoke operator() on that item.
   * <p/>
   * This interface is non-blocking. Returns 'success' if an item was
   * processed. Returns 'item_not_available' if no item can be processed now
   * but more may arrive in the future, or if token limit is reached. Returns
   * 'end_of_stream' if there are no more items to process.
   *
   * @return
   */
  public final Result tryProcessItem()
  {
    return internalProcessItem(false);
  }

  /**
   * Wait until a data item becomes available, and invoke operator() on that
   * item.
   * <p/>
   * This interface is blocking. Returns 'success' if an item was processed.
   * Returns 'end_of_stream' if there are no more items to process. Never
   * returns 'item_not_available', as it blocks until another return condition
   * applies.
   *
   * @return
   */
  public final Result processItem()
  {
    return internalProcessItem(true);
  }

  /**
   * Internal routine for item processing
   *
   * @param is_blocking
   * @return
   */
  private Result internalProcessItem(boolean is_blocking)
  {
    OrderedBuffer.TaskInfo<T> info = new OrderedBuffer.TaskInfo<T>(); // internal::empty_info;

    if (prev_filter_in_pipeline == null)
    {
      if (my_pipeline._endOfInput)
      {
        return Result.end_of_stream;
      }
      while (my_pipeline._inputTokens == null)
      {
        if (is_blocking)
        {
          TBB.Yield();
        }
        else
        {
          return Result.item_not_available;
        }
      }
      info._object = operator(info._object);
      if (info._object != null)
      {
        my_pipeline._inputTokens.getAndDecrement();
        if (isOrdered())
        {
          info._token = my_pipeline._tokenCounter.get();
          info._tokenReady = true;
        }
        my_pipeline._tokenCounter.getAndIncrement(); // ideally, with
        // relaxed
        // semantics
      }
      else
      {
        my_pipeline._endOfInput = true;
        return Result.end_of_stream;
      }
    }
    else
    { /* this is not an input filter */
      while (!input_buffer.returnItem(info, /* advance= */true))
      {
        if (my_pipeline._endOfInput && input_buffer.getLowToken() == my_pipeline._tokenCounter.get())
        {
          return Result.end_of_stream;
        }
        if (is_blocking)
        {
          TBB.Yield();
        }
        else
        {
          return Result.item_not_available;
        }
      }
      info._object = operator(info._object);
    }
    if (next_filter_in_pipeline != null)
    {
      next_filter_in_pipeline.input_buffer.putItem(info);
    }
    else
    {
      my_pipeline._inputTokens.getAndIncrement();
    }

    return Result.success;
  }

  public static enum Result
  {
    /**
     * item was processed
     */
    success,

    /**
     * item is currently not available
     */
    item_not_available,

    /**
     * there are no more items to process
     */
    end_of_stream
  }

}

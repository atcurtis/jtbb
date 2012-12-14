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

public abstract class Filter<T>
{

  private static final Object __not_in_pipeline__ = new Object();

  /**
   * Value used to mark "not in pipeline"
   *
   * @return
   */
  @SuppressWarnings("unchecked")
  Filter<T> not_in_pipeline()
  {
    return (Filter<T>) __not_in_pipeline__;
  }

  /**
   * The lowest bit 0 is for parallel vs. serial
   */
  static final int filter_is_serial = 0x1;

  /**
   * 4th bit distinguishes ordered vs unordered filters.
   * <p/>
   * The bit was not set for parallel filters in TBB 2.1 and earlier, but
   * is_ordered() function always treats parallel filters as out of order.
   */
  static final int filter_is_out_of_order = 0x1 << 4;

  /**
   * 5th bit distinguishes thread-bound and regular filters.
   */
  static final int filter_is_bound = 0x1 << 5;

  public static enum Mode
  {
    /**
     * processes multiple items in parallel and in no particular order
     */
    parallel(filter_is_out_of_order),

    /**
     * processes items one at a time; all such filters process items in the
     * same order
     */
    serial_in_order(filter_is_serial),

    /**
     * processes items one at a time and in no particular order
     */
    serial_out_of_order(filter_is_serial | filter_is_out_of_order);

    public final int my_bits;

    private Mode(int bits)
    {
      my_bits = bits;
    }
  }

  protected Filter(boolean is_serial)
  {
    next_filter_in_pipeline = not_in_pipeline();
    my_filter_mode = is_serial ? Mode.serial_in_order.my_bits : Mode.parallel.my_bits;
    prev_filter_in_pipeline = not_in_pipeline();
  }

  protected Filter(Mode filter_mode)
  {
    next_filter_in_pipeline = not_in_pipeline();
    my_filter_mode = filter_mode.my_bits;
    prev_filter_in_pipeline = not_in_pipeline();
  }

  protected Filter(int filter_mode)
  {
    next_filter_in_pipeline = not_in_pipeline();
    my_filter_mode = filter_mode;
    prev_filter_in_pipeline = not_in_pipeline();
  }

  /**
   * True if filter is serial.
   *
   * @return
   */
  public final boolean isSerial()
  {
    return (my_filter_mode & filter_is_serial) == filter_is_serial;
  }

  /**
   * True if filter must receive stream in order.
   *
   * @return
   */
  public final boolean isOrdered()
  {
    return (my_filter_mode & (filter_is_out_of_order | filter_is_serial)) == filter_is_serial;
  }

  /**
   * True if filter is thread-bound.
   *
   * @return
   */
  public final boolean isBound()
  {
    return (my_filter_mode & filter_is_bound) == filter_is_bound;
  }

  /**
   * Operate on an item from the input stream, and return item for output
   * stream.
   * <p/>
   * Returns NULL if filter is a sink.
   *
   * @param item
   * @return
   */
  public abstract T operator(T item);

  /**
   * Destroys item if pipeline was cancelled.
   * <p/>
   * Note it can be called concurrently even for serial filters.
   *
   * @param item
   */
  public void finalize(T item)
  {
  }

  /**
   * Pointer to next filter in the pipeline.
   */
  Filter<T> next_filter_in_pipeline;

  /**
   * Buffer for incoming tokens, or NULL if not required.
   * <p/>
   * The buffer is required if the filter is serial or follows a thread-bound
   * one.
   */
  OrderedBuffer<T> input_buffer;

  /**
   * Storage for filter mode and dynamically checked implementation version.
   */
  final int my_filter_mode;

  /**
   * Pointer to previous filter in the pipeline.
   */
  Filter<T> prev_filter_in_pipeline;

  /**
   * Pointer to the pipeline.
   */
  Pipeline<T> my_pipeline;

  /**
   * Pointer to the next "segment" of filters, or NULL if not required.
   * <p/>
   * In each segment, the first filter is not thread-bound but follows a
   * thread-bound one.
   */
  Filter<T> next_segment;
}

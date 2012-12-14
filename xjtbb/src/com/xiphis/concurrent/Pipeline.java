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
import com.xiphis.concurrent.internal.StageTask;
import com.xiphis.concurrent.internal.TBB;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class Pipeline<T>
{
  private static final Logger LOG = TBB.LOG;

  /**
   * Number of idle tokens waiting for input stage.
   */
  final AtomicInteger _inputTokens;

  /**
   * Though the current implementation declares the destructor virtual, do not
   * rely on this detail. The virtualness is deprecated and may disappear in
   * future versions of TBB.
   */
  // virtual __TBB_EXPORTED_METHOD ~pipeline();
  /**
   * Global counter of tokens
   */
  final AtomicInteger _tokenCounter;
  private final Factory<PipelineTask> _pipelineTaskFactory = new Factory<PipelineTask>()
  {
    public PipelineTask construct(Object... arguments)
    {
      return new PipelineTask();
    }
  };
  /**
   * Pointer to first filter in the pipeline.
   */
  Filter<T> _filterList;
  /**
   * Pointer to location where address of next filter to be added should be
   * stored.
   */
  Filter<T> _filterEnd;
  /**
   * task who's reference count is used to determine when all stages are done.
   */
  RootTask _endCounter;
  /**
   * False until fetch_input returns NULL.
   */
  boolean _endOfInput;
  /**
   * True if the pipeline contains a thread-bound filter; false otherwise.
   */
  boolean _hasThreadBoundFilters;

  // ! Construct empty pipeline.
  public Pipeline()
  {
    _inputTokens = new AtomicInteger(0);
    _tokenCounter = new AtomicInteger(0);
  }

  /**
   * Add filter to end of pipeline.
   *
   * @param filter_
   */
  public final void addFilter(Filter<T> filter_)
  {
    if (TBB.USE_ASSERT)
    {
      assert filter_.prev_filter_in_pipeline == filter_.not_in_pipeline() : "filter already part of pipeline?";
      assert filter_.next_filter_in_pipeline == filter_.not_in_pipeline() : "filter already part of pipeline?";
      assert _endCounter == null : "invocation of add_filter on running pipeline";
    }
    filter_.my_pipeline = this;
    filter_.prev_filter_in_pipeline = _filterEnd;
    if (_filterList == null)
    {
      _filterList = filter_;
    }
    else
    {
      _filterEnd.next_filter_in_pipeline = filter_;
    }
    filter_.next_filter_in_pipeline = null;
    _filterEnd = filter_;

    if (filter_.isSerial())
    {
      if (filter_.isBound())
      {
        _hasThreadBoundFilters = true;
      }
      filter_.input_buffer = new OrderedBuffer<T>(filter_.isOrdered(), filter_.isBound());
    }
    else
    {
      if (filter_.prev_filter_in_pipeline != null && filter_.prev_filter_in_pipeline.isBound())
      {
        filter_.input_buffer = new OrderedBuffer<T>(false, false);
      }
    }

  }

  /**
   * Run the pipeline to completion.
   *
   * @param max_number_of_live_tokens
   */
  public final void run(int max_number_of_live_tokens)
  {
    TaskGroupContext context = new TaskGroupContext();
    run(max_number_of_live_tokens, context);
  }

  /**
   * Run the pipeline to completion with user-supplied context.
   *
   * @param max_number_of_live_tokens
   * @param context
   */
  public final void run(int max_number_of_live_tokens, TaskGroupContext context)
  {
    if (TBB.USE_ASSERT) assert max_number_of_live_tokens > 0 : "pipeline::run must have at least one token";
    if (TBB.USE_ASSERT) assert _endCounter == null : "pipeline already running?";
    if (_filterList != null)
    {
      if (_filterList.next_filter_in_pipeline != null || !_filterList.isSerial())
      {
        try
        {
          _endOfInput = false;
          _endCounter = Task.allocateRoot(context, new Factory<RootTask>()
          {
            public RootTask construct(Object... arguments)
            {
              return new RootTask();
            }
          });
          _inputTokens.set(max_number_of_live_tokens);
          // Start execution of tasks
          Task.spawnRootAndWait(_endCounter);
        }
        finally
        {
          if (_endCounter.isCancelled())
          { // Pipeline was cancelled
            clearFilters();
          }
          _endCounter = null;
        }
      }
      else
      {
        // There are no filters, and thus no parallelism is possible.
        // Just drain the input stream.
        while (_filterList.operator(null) != null)
        {
          continue;
        }
      }
    }
  }

  /**
   * Remove all filters from the pipeline.
   */
  public final void clear()
  {
    Filter<T> next;
    for (Filter<T> f = _filterList; f != null; f = next)
    {
      OrderedBuffer<T> b = f.input_buffer;
      if (b != null)
      {
        // delete b;
        f.input_buffer = null;
      }
      next = f.next_filter_in_pipeline;
      f.next_filter_in_pipeline = f.not_in_pipeline();
      f.prev_filter_in_pipeline = f.not_in_pipeline();
      f.my_pipeline = null;
      f.next_segment = null;
    }
    _filterList = _filterEnd = null;
  }

  /**
   * Remove filter from pipeline.
   *
   * @param filter_
   */
  final void removeFilter(Filter<T> filter_)
  {
    if (filter_ == _filterList)
    {
      _filterList = filter_.next_filter_in_pipeline;
    }
    else
    {
      if (TBB.USE_ASSERT) assert filter_.prev_filter_in_pipeline != null : "filter list broken?";
      filter_.prev_filter_in_pipeline.next_filter_in_pipeline = filter_.next_filter_in_pipeline;
    }
    if (filter_ == _filterEnd)
    {
      _filterEnd = filter_.prev_filter_in_pipeline;
    }
    else
    {
      if (TBB.USE_ASSERT) assert filter_.next_filter_in_pipeline != null : "filter list broken?";
      filter_.next_filter_in_pipeline.prev_filter_in_pipeline = filter_.prev_filter_in_pipeline;
    }
    OrderedBuffer<T> b = filter_.input_buffer;
    if (b != null)
    {
      // delete b;
      filter_.input_buffer = null;
    }
    filter_.next_filter_in_pipeline = filter_.prev_filter_in_pipeline = filter_.not_in_pipeline();
    filter_.next_segment = null;
    filter_.my_pipeline = null;
  }

  /**
   * Does clean up if pipeline is cancelled or exception occured
   */
  final void clearFilters()
  {
    for (Filter<T> f = _filterList; f != null; f = f.next_filter_in_pipeline)
    {
      OrderedBuffer<T> b = f.input_buffer;
      if (b != null)
      {
        b.clear(f);
      }
    }
  }

  private final class PipelineTask extends Task implements StageTask<T>
  {
    private final Info _taskInfo;
    /**
     * True if this task has not yet read the input.
     */
    boolean _atStart;
    private Filter<T> _filter;

    /**
     * Construct stage_task for first stage in a pipeline.
     * <p/>
     * Such a stage has not read any input yet.
     */
    public PipelineTask()
    {
      _taskInfo = new Info();
      _filter = _filterList;
      _atStart = true;
    }

    /**
     * Construct stage_task for a subsequent stage in a pipeline.
     *
     * @param filter_
     * @param info
     */
    public PipelineTask(Filter<T> filter_, OrderedBuffer.TaskInfo<T> info)
    {
      _taskInfo = new Info(info);
      _filter = filter_;
      _atStart = false;
    }

    /**
     * The virtual task execution method
     */
    @Override
    public Task execute()
    {
      if (TBB.USE_ASSERT) assert !_atStart || _taskInfo._object == null;
      if (TBB.USE_ASSERT) assert !_filter.isBound();
      if (_atStart)
      {
        if (_filter.isSerial())
        {
          _taskInfo._object = _filter.operator(_taskInfo._object);
          if (_taskInfo._object != null)
          {
            if (_filter.isOrdered())
            {
              _taskInfo._token = _tokenCounter.getAndIncrement(); // ideally,
              // with
              // relaxed
              // semantics
              _taskInfo._tokenReady = true;
            }
            else if (_hasThreadBoundFilters)
            {
              _tokenCounter.getAndIncrement();
            } // ideally, with relaxed semantics
            // ITT_NOTIFY( sync_releasing, &my_pipeline._inputTokens );
            if (_inputTokens.decrementAndGet() > 0)
            {
              spawn(allocateAdditionalChildOf(parent(), _pipelineTaskFactory));
            }
          }
          else
          {
            _endOfInput = true;
            return null;
          }
        }
        else /* not is_serial */
        {
          if (_endOfInput)
          {
            return null;
          }
          if (_hasThreadBoundFilters)
          {
            _tokenCounter.getAndIncrement();
          }
          // ITT_NOTIFY( sync_releasing, &my_pipeline._inputTokens );
          if (_inputTokens.decrementAndGet() > 0)
          {
            spawn(allocateAdditionalChildOf(parent(), _pipelineTaskFactory));
          }
          _taskInfo._object = _filter.operator(_taskInfo._object);
          if (_taskInfo._object == null)
          {
            _endOfInput = true;
            if (_hasThreadBoundFilters)
            {
              _tokenCounter.getAndDecrement();
            }
            return null;
          }
        }
        _atStart = false;
      }
      else
      {
        _taskInfo._object = _filter.operator(_taskInfo._object);
        if (_filter.isSerial())
        {
          _filter.input_buffer.noteDone(_taskInfo._token, this);
        }
      }
      Task next = null;
      _filter = _filter.next_filter_in_pipeline;
      if (_filter != null)
      {
        // There is another filter to execute.
        // Crank up priority a notch.
        addToDepth(1);
        process_another_stage:
        while (_filter.isSerial())
        {
          // The next filter must execute tokens in order
          if (_filter.input_buffer.putToken(this))
          {
            // Can't proceed with the same item
            if (_filter.isBound())
            {
              // Find the next non-thread-bound filter
              do
              {
                _filter = _filter.next_filter_in_pipeline;
              } while (_filter != null && _filter.isBound());
              // Check if there is an item ready to process
              if (_filter != null && _filter.input_buffer.returnItem(_taskInfo, !_filter.isSerial()))
              {
                break process_another_stage;
              }
            }
            _filter = null; // To prevent deleting _object twice
            // if exception occurs
            return null;
          }
          break;
        }                                /*
                                 * A semi-hackish way to reexecute the same task object
				 * immediately without spawning. recycle_as_continuation marks
				 * the task for future execution, and then 'this' pointer is
				 * returned to bypass spawning.
				 */
        recycleAsContinuation();
        next = this;
      }
      else
      {
        // Reached end of the pipe. Inject a new token.
        // The token must be injected before execute() returns, in order
        // to prevent the
        // parent's reference count from prematurely reaching 0.
        setDepth(parent().depth() + 1);
        if (_inputTokens.incrementAndGet() == 1)
        {
          // ITT_NOTIFY( sync_acquired, &my_pipeline._inputTokens );
          if (!_endOfInput && !_filterList.isBound())
          {
            spawn(allocateAdditionalChildOf(parent(), _pipelineTaskFactory));
          }
        }
      }
      return next;
    }

    public boolean isTokenReady()
    {
      return _taskInfo._tokenReady;
    }

    // ~stage_task()
    // {
    // if (_filter && _object && (_filter->my_filter_mode &
    // filter::version_mask) >= __TBB_PIPELINE_VERSION(4)) {
    // __TBB_ASSERT(is_cancelled(),
    // "Tryning to finalize the task that wasn't cancelled");
    // _filter->finalize(_object);
    // _object = NULL;
    // }
    // }

    public void setTokenReady(boolean b)
    {
      _taskInfo._tokenReady = b;
    }

    public int getToken()
    {
      return _taskInfo._token;
    }

    public void setToken(int i)
    {
      _taskInfo._token = i;
    }

    /**
     * Puts current task information
     */
    public void putTaskInfo(OrderedBuffer.TaskInfo<T> where_to_put)
    {
      where_to_put._object = _taskInfo._object;
      where_to_put._token = _taskInfo._token;
      where_to_put._tokenReady = _taskInfo._tokenReady;
      where_to_put._valid = true;
    }

    /**
     * Creates and spawns stage_task from _taskInfo
     */
    public void spawnStageTask(final OrderedBuffer.TaskInfo<T> info)
    {
      PipelineTask clone = allocateAdditionalChildOf(parent(), new Factory<PipelineTask>()
      {
        public PipelineTask construct(Object... arguments)
        {
          return new PipelineTask(_filter, info);
        }
      });
      spawn(clone);
    }

    class Info extends OrderedBuffer.TaskInfo<T>
    {
      public Info()
      {

      }

      public Info(OrderedBuffer.TaskInfo<T> info)
      {
        super(info);
      }

      final PipelineTask task()
      {
        return PipelineTask.this;
      }
    }
  }

  ;

  private final class RootTask extends Task
  {
    boolean do_segment_scanning;

    public RootTask()
    {
      if (TBB.USE_ASSERT) assert _filterList != null;
      Filter<T> first = _filterList;
      // Scanning the pipeline for segments
      Filter<T> head_of_previous_segment = first;
      for (Filter<T> subfilter = first.next_filter_in_pipeline; subfilter != null;
           subfilter = subfilter.next_filter_in_pipeline)
      {
        if (subfilter.prev_filter_in_pipeline.isBound() && !subfilter.isBound())
        {
          do_segment_scanning = true;
          head_of_previous_segment.next_segment = subfilter;
          head_of_previous_segment = subfilter;
        }
      }
    }

    @Override
    public Task execute()
    {
      if (!_endOfInput)
      {
        if (!_filterList.isBound())
        {
          if (_inputTokens.get() > 0)
          {
            recycleAsContinuation();
            setRefCount(1);
            return allocateChild(_pipelineTaskFactory);
          }
        }
      }
      if (do_segment_scanning)
      {
        Filter<T> current_filter = _filterList.next_segment;                                /*
				 * first non-thread-bound filter that follows thread-bound one
				 * and may have valid items to process
				 */
        Filter<T> first_suitable_filter = current_filter;
        while (current_filter != null)
        {
          // __TBB_ASSERT( !current_filter->is_bound(),
          // "filter is thread-bound?" );
          // __TBB_ASSERT(
          // current_filter->prev_filter_in_pipeline->is_bound(),
          // "previous filter is not thread-bound?" );
          if (!_endOfInput || (_tokenCounter.get() - current_filter.input_buffer.getLowToken()) > 0)
          {
            final OrderedBuffer.TaskInfo<T> info = new OrderedBuffer.TaskInfo<T>();
            if (current_filter.input_buffer.returnItem(info, !current_filter.isSerial()))
            {
              final Filter<T> filter = current_filter;
              setRefCount(1);
              recycleAsContinuation();
              return allocateChild(new Factory<PipelineTask>()
              {
                public PipelineTask construct(Object... arguments)
                {
                  return new PipelineTask(filter, info);
                }
              });
            }
            current_filter = current_filter.next_segment;
            if (current_filter == null)
            {
              if (!_endOfInput)
              {
                recycleAsContinuation();
                return this;
              }
              current_filter = first_suitable_filter;
              TBB.Yield();
            }
          }
          else
          {
						/*
						 * The preceding pipeline segment is empty. Fast-forward
						 * to the next post-TBF segment.
						 */
            first_suitable_filter = first_suitable_filter.next_segment;
            current_filter = first_suitable_filter;
          }
        } /* end of while */
        return null;
      }
      else
      {
        if (!_endOfInput)
        {
          recycleAsContinuation();
          return this;
        }
        return null;
      }
    }


  }
}

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

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author atcurtis
 */
public final class Parallel
{

  private Parallel()
  {
  }

  @SuppressWarnings("unchecked")
  private static <R extends RangeConcept<R>.Range, B extends ScanBody<R, B>> //
  SumNode<R, B>[] newSumNodeHolder()
  {
    return new SumNode[1];
  }

  @SuppressWarnings("unchecked")
  private static <R extends RangeConcept<R>.Range, B extends ScanBody<R, B>> //
  FinalSum<R, B>[] newFinalSumHolder()
  {
    return new FinalSum[1];
  }

  // Classes for parallelFor()

  /**
   * @param <R>
   * @param <B>
   * @param range
   * @param body
   */
  public static <R extends RangeConcept<R>.Range, B extends Body<R, B>> //
  void parallelFor(R range, B body)
  {
    TaskGroupContext context = new TaskGroupContext();
    StartFor.run(range, body, new AutoPartitionerConcept<R>().clone(null), context);
  }

  // Classes for parallelReduce()

  /**
   * @param <R>
   * @param <B>
   * @param range
   * @param body
   * @param context
   */
  public static <R extends RangeConcept<R>.Range, B extends Body<R, B>> //
  void parallelFor(R range, B body, TaskGroupContext context)
  {
    StartFor.run(range, body, new AutoPartitionerConcept<R>().clone(null), context);
  }

  /**
   * @param <R>
   * @param <B>
   * @param <P>
   * @param range
   * @param body
   * @param partitioner
   */
  public static <R extends RangeConcept<R>.Range, B extends Body<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
  void parallelFor(R range, B body, P partitioner)
  {
    TaskGroupContext context = new TaskGroupContext();
    StartFor.run(range, body, partitioner, context);
  }

  ;

  /**
   * @param <R>
   * @param <B>
   * @param <P>
   * @param range
   * @param body
   * @param partitioner
   * @param context
   */
  public static <R extends RangeConcept<R>.Range, B extends Body<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
  void parallelFor(R range, B body, P partitioner, TaskGroupContext context)
  {
    StartFor.run(range, body, partitioner, context);
  }

  // Classes for parallelScan()

  /**
   * @param <R>
   * @param <B>
   * @param range
   * @param body
   */
  public static <R extends RangeConcept<R>.Range, B extends ReduceBody<R, B>> //
  void parallelReduce(R range, B body)
  {
    TaskGroupContext context = new TaskGroupContext();
    StartReduce.run(range, body, new AutoPartitionerConcept<R>().clone(null), context);
  }

  /**
   * @param <R>
   * @param <B>
   * @param range
   * @param body
   * @param context
   */
  public static <R extends RangeConcept<R>.Range, B extends ReduceBody<R, B>> //
  void parallelReduce(R range, B body, TaskGroupContext context)
  {
    StartReduce.run(range, body, new AutoPartitionerConcept<R>().clone(null), context);
  }

  /**
   * @param <R>
   * @param <B>
   * @param <P>
   * @param range
   * @param body
   * @param partitioner
   */
  public static <R extends RangeConcept<R>.Range, B extends ReduceBody<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
  void parallelReduce(R range, B body, P partitioner)
  {
    TaskGroupContext context = new TaskGroupContext();
    StartReduce.run(range, body, partitioner, context);
  }

  ;

  /**
   * @param <R>
   * @param <B>
   * @param <P>
   * @param range
   * @param body
   * @param partitioner
   * @param context
   */
  public static <R extends RangeConcept<R>.Range, B extends ReduceBody<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
  void parallelReduce(R range, B body, P partitioner, TaskGroupContext context)
  {
    StartReduce.run(range, body, partitioner, context);
  }

  /**
   * @param <R>
   * @param <B>
   * @param range
   * @param body
   */
  public static <R extends RangeConcept<R>.Range, B extends ScanBody<R, B>> //
  void parallelScan(R range, B body)
  {
    TaskGroupContext context = new TaskGroupContext();
    StartScan.run(range, body, new AutoPartitionerConcept<R>().clone(null), context);
  }

  ;

  /**
   * @param <R>
   * @param <B>
   * @param range
   * @param body
   * @param context
   */
  public static <R extends RangeConcept<R>.Range, B extends ScanBody<R, B>> //
  void parallelScan(R range, B body, TaskGroupContext context)
  {
    StartScan.run(range, body, new AutoPartitionerConcept<R>().clone(null), context);
  }

  // public static methods

  /**
   * @param <R>
   * @param <B>
   * @param <P>
   * @param range
   * @param body
   * @param partitioner
   */
  public static <R extends RangeConcept<R>.Range, B extends ScanBody<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
  void parallelScan(R range, B body, P partitioner)
  {
    TaskGroupContext context = new TaskGroupContext();
    StartScan.run(range, body, partitioner, context);
  }

  /**
   * @param <R>
   * @param <B>
   * @param <P>
   * @param range
   * @param body
   * @param partitioner
   * @param context
   */
  public static <R extends RangeConcept<R>.Range, B extends ScanBody<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
  void parallelScan(R range, B body, P partitioner, TaskGroupContext context)
  {
    StartScan.run(range, body, partitioner, context);
  }

  // ! Wrapper method to initiate the sort by calling parallel_for.
  private static <T> //
  void parallelQuickSort(T[] array, int begin, int end, Comparator<T> comp)
  {
    TaskGroupContext my_context = new TaskGroupContext();
    final int serial_cutoff = 9;

    do_parallel_quick_sort:
    for (; ; )
    {
      if (TBB.USE_ASSERT) assert begin + serial_cutoff < end : "min_parallel_size is smaller than serial cutoff?";
      int k;
      for (k = begin; k != begin + serial_cutoff; ++k)
      {
        if (comp.compare(array[k + 1], array[k]) < 0)
        {
          break do_parallel_quick_sort;
        }
      }

      parallelFor(new IntRangeConcept().newInstance(k + 1, end), new QuickSortPretestBody<T>(array, comp),
                  new AutoPartitionerConcept<IntRangeConcept.IntRange>().clone(null), my_context);

      if (!my_context.isGroupExecutionCancelled())
      {
        return;
      }
    }

    QuickSortRangeConcept<T>.QuickSortRange r = null;
    parallelFor(new QuickSortRangeConcept<T>(array, comp).newInstance(begin, end - begin), new QuickSortBody<T>(),
                auto_partitioner(r));
    // new
    // AutoPartitionerConcept<QuickSortRangeConcept<T>.QuickSortRange>().clone(null));
  }

  private static <R extends RangeConcept<R>.Range> //
  AutoPartitionerConcept<R>.AutoPartitioner auto_partitioner(R dummy)
  {
    return new AutoPartitionerConcept<R>().clone(null);
  }

  /**
   * @param <T>
   * @param array
   * @param begin
   * @param end
   * @param comp
   */
  public static <T> void parallelSort(T[] array, int begin, int end, Comparator<T> comp)
  {
    parallelQuickSort(array, begin, end, comp);
  }

  /**
   * @param <T>
   * @param array
   * @param comp
   */
  public static <T> void parallelSort(T[] array, Comparator<T> comp)
  {
    parallelQuickSort(array, 0, array.length, comp);
  }

  /**
   * @param <T>
   * @param array
   * @param begin
   * @param end
   */
  public static <T extends Comparable<T>> void parallelSort(T[] array, int begin, int end)
  {
    parallelQuickSort(array, begin, end, new Comparator<T>()
    {
      public int compare(T a, T b)
      {
        return a.compareTo(b);
      }
    });
  }

  /**
   * @param <T>
   * @param array
   */
  public static <T extends Comparable<T>> void parallelSort(T[] array)
  {
    parallelQuickSort(array, 0, array.length, new Comparator<T>()
    {
      public int compare(T a, T b)
      {
        return a.compareTo(b);
      }
    });
  }

  private enum ReductionContext
  {
    root, left_child, right_child
  }

  public interface Body<R extends RangeConcept<R>.Range, B extends Body<R, B>> extends Cloneable
  {
    /**
     * Apply body to range
     *
     * @param range
     */
    void apply(R range);

    B clone();
  }

  public interface ReduceBody<R extends RangeConcept<R>.Range, B extends ReduceBody<R, B>> extends Body<R, B>
  {
    /**
     * Split the range. Must be able to run the apply() and join() methods
     * concurrently.
     *
     * @return
     */
    B split();

    /**
     * Join results. The result in rhs should be merged into the result of
     * this.
     *
     * @param rhs
     */
    void join(B rhs);
  }

  public interface ScanBody<R extends RangeConcept<R>.Range, B extends ScanBody<R, B>> extends Body<R, B>
  {
    /**
     * Pre-process iterations for range.
     *
     * @param range
     */
    void prescan(R range);

    /**
     * Split range so that they can accumulate seperately
     *
     * @return
     */
    B split();

    /**
     * Merge preprocessing state of a into this, where a was created earlier
     * by using split().
     *
     * @param a
     */
    void reverseJoin(B a);

    /**
     * Assign state of b to this
     *
     * @param b
     */
    void assign(B b);
  }

  // Classes for QuickSort

  private static class StartFor<R extends RangeConcept<R>.Range, B extends Body<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
      extends Task
  {
    private final R my_range;
    private final B my_body;
    private final P my_partition;
    private final Factory<StartFor<R, B, P>> startForFactory = new Factory<StartFor<R, B, P>>()
    {
      public StartFor<R, B, P> construct(Object... arguments)
      {
        // TODO Auto-generated method stub
        return new StartFor<R, B, P>(StartFor.this);
      }
    };

    // ! Constructor for root task.
    public StartFor(R range, B body, P partitioner)
    {
      my_range = range.concept().clone(range);
      my_body = body.clone();
      my_partition = partitioner.concept().clone(partitioner);
    }

    /**
     * this becomes left child. Newly constructed object is right child.
     */
    public StartFor(StartFor<R, B, P> parent)
    {
      my_range = parent.my_range.concept().split(parent.my_range);
      my_body = parent.my_body.clone();
      my_partition = parent.my_partition.concept().split(parent.my_partition);
      my_partition.setAffinity(this);
    }

    // ! Splitting constructor used to generate children.

    static <R extends RangeConcept<R>.Range, B extends Body<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
    void run(final R range, final B body, final P partitioner)
    {
      if (!range.isEmpty())
      {
        // Bound context prevents exceptions from body to affect nesting
        // or sibling algorithms,
        // and allows users to handle exceptions safely by wrapping
        // parallel_for in the try-block.
        TaskGroupContext context = new TaskGroupContext();
        StartFor<R, B, P> a = Task.allocateRoot(context, new Factory<StartFor<R, B, P>>()
        {
          public StartFor<R, B, P> construct(Object... arguments)
          {
            return new StartFor<R, B, P>(range, body, partitioner);
          }
        });

        Task.spawnRootAndWait(a);
      }
    }

    static <R extends RangeConcept<R>.Range, B extends Body<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
    void run(final R range, final B body, final P partitioner, TaskGroupContext context)
    {
      if (!range.isEmpty())
      {
        StartFor<R, B, P> a = Task.allocateRoot(context, new Factory<StartFor<R, B, P>>()
        {
          public StartFor<R, B, P> construct(Object... arguments)
          {
            return new StartFor<R, B, P>(range, body, partitioner);
          }
        });

        Task.spawnRootAndWait(a);
      }
    }    // ! Update affinity info, if any.

    @Override
    public void noteAffinity(int id)
    {
      my_partition.noteAffinity(id);
    }

    @Override
    public Task execute()
    {
      if (!my_range.isDivisible() || my_partition.shouldExecuteRange(my_range, this))
      {
        my_body.apply(my_range);
        return my_partition.continueAfterExecuteRange(this);
      }
      else
      {
        EmptyTask c = allocateContinuation(Scheduler._emptyTaskFactory);
        recycleAsChildOf(c);
        c.setRefCount(2);
        boolean delay = my_partition.decideWhetherToDelay();
        StartFor<R, B, P> b = c.allocateChild(startForFactory);
        my_partition.spawnOrDelay(delay, this, b);
        return this;
      }
    }


  }

  // ! Task type use to combine the partial results of parallel_reduce with
  // affinity_partitioner.
  private static class FinishReduce<R extends RangeConcept<R>.Range, B extends ReduceBody<R, B>> extends Task
  {
    // ! Pointer to body, or NULL if the left child has not yet finished.
    final AtomicReference<B> my_body;
    private final ReductionContext my_context;
    private final Class<B> my_body_class;
    boolean has_right_zombie;

    @SuppressWarnings("unchecked")
    FinishReduce(ReductionContext context, Class<? extends ReduceBody> body_class)
    {
      my_body = new AtomicReference<B>(null);
      has_right_zombie = false;
      my_context = context;
      my_body_class = (Class<B>) body_class;
    }

    @SuppressWarnings("unchecked")
    FinishReduce<R, B> reduceParent()
    {
      return (FinishReduce<R, B>) parent();
    }

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (has_right_zombie)
      {
        // Right child was stolen.
        B s = my_body_class.newInstance();
        my_body.get().join(s);
        // s->~Body();
      }
      if (my_context == ReductionContext.left_child)
      {
        FinishReduce<R, B> parent = reduceParent();
        parent.my_body.set(my_body.get());
      }
      return null;
    }
  }

  // ! Task type used to split the work of parallel_reduce with
  // affinity_partitioner.
  private static class StartReduce<R extends RangeConcept<R>.Range, B extends ReduceBody<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
      extends Task
  {
    // typedef finish_reduce<Body> finish_type;
    private final AtomicReference<B> my_body;
    private final R my_range;
    private final Factory<FinishReduce<R, B>> finishReduceFactory;
    private final Factory<StartReduce<R, B, P>> startReduceFactory = new Factory<StartReduce<R, B, P>>()
    {
      public StartReduce<R, B, P> construct(Object... arguments)
      {
        return new StartReduce<R, B, P>(StartReduce.this);
      }
    };
    private P my_partition;
    private ReductionContext my_context;

    // ! Constructor used for root task
    StartReduce(R range, B body, P partitioner)
    {
      my_body = new AtomicReference<B>(body);
      my_range = range.concept().clone(range);
      my_partition.concept().clone(partitioner);
      my_context = ReductionContext.root;
      finishReduceFactory = newFinishReduceFactory();
    }

    // ! Splitting constructor used to generate children.

    /**
     * this becomes left child. Newly constructed object is right child.
     */
    StartReduce(StartReduce<R, B, P> parent)
    {
      my_body = parent.my_body;
      my_range = parent.my_range.concept().split(parent.my_range);
      my_partition = parent.my_partition.concept().split(parent.my_partition);
      my_context = ReductionContext.right_child;
      finishReduceFactory = newFinishReduceFactory();
      my_partition.setAffinity(this);
      parent.my_context = ReductionContext.left_child;
    }

    public static <R extends RangeConcept<R>.Range, B extends ReduceBody<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
    void run(final R range, final B body, final P partitioner)
    {
      if (!range.isEmpty())
      {
        // Bound context prevents exceptions from body to affect nesting
        // or sibling algorithms,
        // and allows users to handle exceptions safely by wrapping
        // parallel_for in the try-block.
        TaskGroupContext context = new TaskGroupContext();
        Task.spawnRootAndWait(Task.allocateRoot(context, new Factory<StartReduce<R, B, P>>()
        {
          public StartReduce<R, B, P> construct(Object... arguments)
          {
            return new StartReduce<R, B, P>(range, body, partitioner);
          }
        }));
      }
    }

    public static <R extends RangeConcept<R>.Range, B extends ReduceBody<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
    void run(final R range, final B body, final P partitioner, TaskGroupContext context)
    {
      if (!range.isEmpty())
      {
        Task.spawnRootAndWait(Task.allocateRoot(context, new Factory<StartReduce<R, B, P>>()
        {
          public StartReduce<R, B, P> construct(Object... arguments)
          {
            return new StartReduce<R, B, P>(range, body, partitioner);
          }
        }));
      }
    }

    @SuppressWarnings("unchecked")
    private Factory<FinishReduce<R, B>> newFinishReduceFactory()
    {
      return new Factory<FinishReduce<R, B>>()
      {
        private final Class<B> my_body_class = (Class<B>) my_body.get().getClass();

        public FinishReduce<R, B> construct(Object... arguments)
        {
          return new FinishReduce<R, B>(my_context, my_body_class);
        }
      };
    }

    @Override
    public Task execute()
    {
      if (my_context == ReductionContext.right_child)
      {
        FinishReduce<R, B> p = reduceParent();
        if (p.my_body.get() == null)
        {
          my_body.set(my_body.get().split());
          p.has_right_zombie = true;
        }
      }
      if (!my_range.isDivisible() || my_partition.shouldExecuteRange(my_range, this))
      {
        my_body.get().apply(my_range);
        if (my_context == ReductionContext.left_child)
        {
          FinishReduce<R, B> parent = reduceParent();
          parent.my_body.set(my_body.get());
        }
        return my_partition.continueAfterExecuteRange(this);
      }
      else
      {
        FinishReduce<R, B> c = allocateContinuation(finishReduceFactory);
        recycleAsChildOf(c);
        c.setRefCount(2);
        boolean delay = my_partition.decideWhetherToDelay();
        StartReduce<R, B, P> b = c.allocateChild(startReduceFactory);
        my_partition.spawnOrDelay(delay, this, b);
        return this;
      }
    }

    @SuppressWarnings("unchecked")
    FinishReduce<R, B> reduceParent()
    {
      return (FinishReduce<R, B>) parent();
    }    // ! Update affinity info, if any

    @Override
    public void noteAffinity(int id)
    {
      my_partition.noteAffinity(id);
    }


  }

  ;

  // ! Performs final scan for a leaf
  private static class FinalSum<R extends RangeConcept<R>.Range, B extends ScanBody<R, B>> extends Task
  {
    final B body;
    // aligned_space<Range,1> range;
    R range;
    // ! Where to put result of last subrange, or NULL if not last subrange.
    B stuff_last;

    FinalSum(B body_)
    {
      body = body_.split();
    }

    void finishConstruction(R range_, B stuff_last_)
    {
      range = range_.concept().clone(range_);
      stuff_last = stuff_last_;
    }

    @Override
    public Task execute()
    {
      body.apply(range);
      if (stuff_last != null)
      {
        stuff_last.assign(body);
      }
      return null;
    }
  }

  // ! Split work to be done in the scan.
  private static class SumNode<R extends RangeConcept<R>.Range, B extends ScanBody<R, B>> extends Task
  {

    private final FinalSum<R, B>[] left_sum;
    private final SumNode<R, B>[] left;
    private final SumNode<R, B>[] right;
    private final R range;
    FinalSum<R, B> incoming;
    FinalSum<R, B> body;
    B stuff_last;
    private boolean left_is_final;

    SumNode(R range_, boolean left_is_final_)
    {
      left_sum = newFinalSumHolder();
      left = newSumNodeHolder();
      right = newSumNodeHolder();
      left_is_final = left_is_final_;
      range = range_.concept().clone(range_);

      // Poison fields that will be set by second pass.
      // poison_pointer(body);
      // poison_pointer(incoming);
    }

    Task createChild(R range, FinalSum<R, B> f, SumNode<R, B> n, FinalSum<R, B> incoming, B stuff_last)
    {
      if (n == null)
      {
        f.recycleAsChildOf(this);
        f.finishConstruction(range, stuff_last);
        return f;
      }
      else
      {
        n.body = f;
        n.incoming = incoming;
        n.stuff_last = stuff_last;
        return n;
      }
    }

    @Override
    public Task execute()
    {
      if (body != null)
      {
        if (incoming != null)
        {
          left_sum[0].body.reverseJoin(incoming.body);
        }
        recycleAsContinuation();
        SumNode<R, B> c = this;
        Task b = c.createChild(range.concept().split(range), left_sum[0], right[0], left_sum[0], stuff_last);
        Task a = !left_is_final ? c.createChild(range, body, left[0], incoming, null) : null;
        setRefCount((a != null ? 0 : 1) + (b != null ? 0 : 1));
        body = null;
        if (a != null)
        {
          spawn(b);
        }
        else
        {
          a = b;
        }
        return a;
      }
      else
      {
        return null;
      }
    }
  }

  // ! Combine partial results
  private static class FinishScan<R extends RangeConcept<R>.Range, B extends ScanBody<R, B>> extends Task
  {
    // typedef sum_node<Range,Body> sum_node_type;
    // typedef final_sum<Range,Body> final_sum_type;
    final FinalSum<R, B>[] sum;
    final SumNode<R, B>[] result;
    SumNode<R, B>[] return_slot;
    FinalSum<R, B> right_zombie;

    FinishScan(SumNode<R, B>[] return_slot_, FinalSum<R, B>[] sum_, SumNode<R, B> result_)
    {
      sum = sum_;
      return_slot = return_slot_;
      result = newSumNodeHolder();
      result[0] = result_;
      // __TBB_ASSERT( !return_slot, NULL );
    }

    @Override
    public Task execute()
    {
      // __TBB_ASSERT(
      // result._refCount()==(result.left!=NULL)+(result.right!=NULL),
      // NULL );
      if (result[0].left != null)
      {
        result[0].left_is_final = false;
      }
      if (right_zombie != null && sum != null)
      {
        sum[0].body.reverseJoin(result[0].left_sum[0].body);
      }
      if (TBB.USE_ASSERT) assert return_slot == null;
      if (right_zombie != null || result[0].right != null)
      {
        return_slot = result;
      }
      else
      {
        destroy(result[0]);
        result[0] = null;
      }
      if (right_zombie != null && sum == null && result[0].right == null)
      {
        destroy(right_zombie);
        right_zombie = null;
      }
      return null;
    }

  }

  // ! Initial task to split the work
  private static class StartScan<R extends RangeConcept<R>.Range, B extends ScanBody<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
      extends Task
  {
    // typedef sum_node<Range,Body> sum_node_type;
    // typedef final_sum<Range,Body> final_sum_type;
    final FinalSum<R, B>[] body;
    /**
     * Null if computing root.
     */
    final SumNode<R, B>[] parent_sum;
    private final Factory<SumNode<R, B>> sumNodeFactory = new Factory<SumNode<R, B>>()
    {
      public SumNode<R, B> construct(Object... arguments)
      {
        return new SumNode<R, B>(range, is_final);
      }
    };
    private final Factory<FinalSum<R, B>> finalSumFactory = new Factory<FinalSum<R, B>>()
    {
      public FinalSum<R, B> construct(Object... arguments)
      {
        return new FinalSum<R, B>(body[0].body);
      }
    };
    /**
     * Non-null if caller is requesting total.
     */
    FinalSum<R, B>[] sum;
    SumNode<R, B>[] return_slot;
    boolean is_final;
    boolean is_right_child;
    R range;
    P partition;

    StartScan(SumNode<R, B>[] return_slot_, StartScan<R, B, P> parent, SumNode<R, B> parent_sum_)
    {
      body = newFinalSumHolder();
      body[0] = parent.body[0];
      sum = parent.sum;
      return_slot = return_slot_;
      parent_sum = newSumNodeHolder();
      parent_sum[0] = parent_sum_;
      is_final = parent.is_final;
      range = parent.range.concept().split(parent.range);
      partition = parent.partition.concept().split(parent.partition);
      // __TBB_ASSERT( !*return_slot, NULL );
    }

    StartScan(SumNode<R, B>[] return_slot_, R range_, FinalSum<R, B> body_, P partitioner_)
    {
      body = newFinalSumHolder();
      body[0] = body_;
      return_slot = return_slot_;
      parent_sum = newSumNodeHolder();
      is_final = true;
      range = range_.concept().clone(range_);
      partition = partitioner_.concept().clone(partitioner_);
      // __TBB_ASSERT( !*return_slot, NULL );
    }

    static <R extends RangeConcept<R>.Range, B extends ScanBody<R, B>, P extends PartitionerConcept<R, P>.Partitioner> //
    void run(final R range, final B body, final P partitioner, TaskGroupContext context)
    {
      if (!range.isEmpty())
      {
        // typedef internal::start_scan<Range,Body,Partitioner>
        // start_pass1_type;
        final SumNode<R, B>[] root = newSumNodeHolder();
        // typedef internal::final_sum<Range,Body> final_sum_type;
        final FinalSum<R, B> temp_body = Task.allocateRoot(context, new Factory<FinalSum<R, B>>()
        {
          public FinalSum<R, B> construct(Object... arguments)
          {
            return new FinalSum<R, B>(body);
          }
        });
        StartScan<R, B, P> pass1 = Task.allocateRoot(context, new Factory<StartScan<R, B, P>>()
        {
          public StartScan<R, B, P> construct(Object... arguments)
          {
            return new StartScan<R, B, P>(root, range, temp_body, partitioner);
          }
        });
        Task.spawnRootAndWait(pass1);
        if (root[0] != null)
        {
          root[0].body = temp_body;
          root[0].incoming = null;
          root[0].stuff_last = body;
          Task.spawnRootAndWait(root[0]);
        }
        else
        {
          body.assign(temp_body.body);
          temp_body.finishConstruction(range, null);
          temp_body.destroy(temp_body);
        }
      }
    }

    @SuppressWarnings("unchecked")
    FinishScan<R, B> finishScanParent()
    {
      return (FinishScan<R, B>) parent();
    }

    @Override
    public Task execute()
    {
      // typedef internal::finish_scan<Range,Body> finish_pass1_type;
      FinishScan<R, B> p = parent_sum[0] != null ? finishScanParent() : null;
      // Inspecting p->result.left_sum would ordinarily be a race
      // condition.
      // But we inspect it only if we are not a stolen task, in which case
      // we
      // know that task assigning to p->result.left_sum has completed.
      boolean treat_as_stolen = is_right_child && (isStolenTask() || body[0] != p.result[0].left_sum[0]);
      if (treat_as_stolen)
      {
        // Invocation is for right child that has been really stolen or
        // needs to be virtually stolen
        p.right_zombie = body[0] = allocateRoot(context(), finalSumFactory);
        is_final = false;
      }

      Task next_task = null;
      if ((is_right_child && !treat_as_stolen) || !range.isDivisible() || partition.shouldExecuteRange(range, this))
      {
        if (is_final)
        {
          body[0].body.apply(range);
        }
        else if (sum != null)
        {
          body[0].body.prescan(range);
        }
        if (sum != null)
        {
          sum[0] = body[0];
        }
        // __TBB_ASSERT( !*return_slot, NULL );
      }
      else
      {
        final SumNode<R, B> result;
        if (parent_sum[0] != null)
        {
          result = allocateAdditionalChildOf(parent_sum[0], sumNodeFactory);
        }
        else
        {
          result = allocateRoot(context(), sumNodeFactory);
        }
        FinishScan<R, B> c = allocateContinuation(new Factory<FinishScan<R, B>>()
        {
          public FinishScan<R, B> construct(Object... arguments)
          {
            return new FinishScan<R, B>(return_slot, sum, result);
          }
        });
        // Split off right child
        StartScan<R, B, P> b = c.allocateChild(new Factory<StartScan<R, B, P>>()
        {
          public StartScan<R, B, P> construct(Object... arguments)
          {
            return new StartScan<R, B, P>(result.right, StartScan.this, result);
          }
        });
        b.is_right_child = true;
        // Left child is recycling of *this. Must recycle this before
        // spawning b,
        // otherwise b might complete and decrement c._refCount() to
        // zero, which
        // would cause c.execute() to run prematurely.
        recycleAsChildOf(c);
        c.setRefCount(2);
        c.spawn(b);
        sum = result.left_sum;
        return_slot = result.left;
        is_right_child = false;
        next_task = this;
        parent_sum[0] = result;
        // __TBB_ASSERT( !*return_slot, NULL );
      }
      return next_task;
    }


  }

  private static class QuickSortRangeConcept<T> extends RangeConcept<QuickSortRangeConcept<T>.QuickSortRange>
  {
    static final int grainsize = 500;
    final Comparator<T> comp;
    final T[] array;

    QuickSortRangeConcept(T[] array_, Comparator<T> comp_)
    {
      array = array_;
      comp = comp_;
    }

    private int median_of_three(int l, int m, int r)
    {
      return comp.compare(array[l], array[m]) < 0 ? //
          (comp.compare(array[m], array[r]) < 0 ? m //
              : (comp.compare(array[l], array[r]) < 0 ? r : l))
          : (comp.compare(array[r], array[m]) < 0 ? m //
              : (comp.compare(array[r], array[l]) < 0 ? r : l));
    }

    private int pseudo_median_of_nine(QuickSortRange range)
    {
      int offset = range.size / 8;
      int begin = range.begin;
      return median_of_three( //
                              median_of_three(begin, begin + offset, begin + offset * 2), //
                              median_of_three(begin + offset * 3, begin + offset * 4, begin + offset * 5), //
                              median_of_three(begin + offset * 6, begin + offset * 7, begin + range.size - 1));

    }

    public QuickSortRange newInstance(int begin, int size)
    {
      return new QuickSortRange(begin, size);
    }

    @Override
    public QuickSortRange clone(QuickSortRange range)
    {
      return new QuickSortRange(range.begin, range.size);
    }

    @Override
    public QuickSortRange split(QuickSortRange range)
    {
      final int array = range.begin;
      int key0 = range.begin;
      int m = pseudo_median_of_nine(range);
      swap(array, m);

      int i = 0;
      int j = range.size;
      // Partition interval [i+1,j-1] with key *key0.
      partition:
      for (; ; )
      {
        if (TBB.USE_ASSERT) assert i < j;
        // Loop must terminate since array[l]==*key0.
        do
        {
          --j;
          if (TBB.USE_ASSERT) assert i <= j : "bad ordering relation?";
        } while (comp.compare(this.array[key0], this.array[array + j]) < 0);
        do
        {
          if (TBB.USE_ASSERT) assert i <= j;
          if (i == j)
          {
            break partition;
          }
          ++i;
        } while (comp.compare(this.array[array + i], this.array[key0]) < 0);
        if (i == j)
        {
          break partition;
        }
        swap(array + i, array + j);
      }

      // Put the partition key were it belongs
      swap(array + j, key0);
      // array[l..j) is less or equal to key.
      // array(j..r) is greater or equal to key.
      // array[j] is equal to key
      i = j + 1;
      int begin = array + i;
      int size = range.size - i;
      range.size = j;
      return new QuickSortRange(begin, size);
    }

    private void swap(int a, int b)
    {
      if (a != b)
      {
        T tmp = array[a];
        array[a] = array[b];
        array[b] = tmp;
      }
    }

    public class QuickSortRange extends RangeConcept<QuickSortRangeConcept<T>.QuickSortRange>.Range
    {

      int begin;
      int size;

      QuickSortRange(int begin_, int size_)
      {
        begin = begin_;
        size = size_;
      }

      @Override
      public int size()
      {
        return size;
      }

      @Override
      public boolean isEmpty()
      {
        return size == 0;
      }

      @Override
      public boolean isDivisible()
      {
        return size >= grainsize;
      }

      public T[] array()
      {
        return array;
      }

      public boolean comp(T a, T b)
      {
        return comp.compare(a, b) < 0;
      }

      public Comparator<T> comparator()
      {
        return comp;
      }
    }
  }

  // ! Body class used to test if elements in a range are presorted
  private static class QuickSortPretestBody<T> implements Body<IntRangeConcept.IntRange, QuickSortPretestBody<T>>
  {
    final T[] array;
    final Comparator<T> comp;

    public QuickSortPretestBody(T[] array_, Comparator<T> comp_)
    {
      array = array_;
      comp = comp_;
    }

    public void apply(IntRangeConcept.IntRange range)
    {
      Task my_task = Task.currentTask();
      int my_end = range._begin + range._size;
      for (int k = range._begin, i = 0; k != my_end; ++k, ++i)
      {
        if ((i & 63) == 0 && my_task.isCancelled())
        {
          break;
        }

        // The k-1 is never out-of-range because the first chunk starts
        // at begin+serial_cutoff+1
        if (comp.compare(array[k], array[k - 1]) < 0)
        {
          my_task.cancelGroupExecution();
          break;
        }

      }
    }

    @Override
    public QuickSortPretestBody<T> clone()
    {
      return new QuickSortPretestBody<T>(array, comp);
    }


  }

  // ! Body class used to sort elements in a range that is smaller than the
  // grainsize.
  private static class QuickSortBody<T> implements Body<QuickSortRangeConcept<T>.QuickSortRange, QuickSortBody<T>>
  {
    @Override
    public QuickSortBody<T> clone()
    {
      return new QuickSortBody<T>();
    }

    public void apply(QuickSortRangeConcept<T>.QuickSortRange range)
    {
      Arrays.sort(range.array(), range.begin, range.begin + range.size, range.comparator());
    }
  }
}

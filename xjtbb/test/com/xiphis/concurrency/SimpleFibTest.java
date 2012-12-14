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
package com.xiphis.concurrency;

import com.xiphis.concurrent.Factory;
import com.xiphis.concurrent.Task;
import com.xiphis.concurrent.TaskScheduler;
import org.testng.annotations.Test;

public class SimpleFibTest
{

  public static final long CUT_OFF = 8;
  public static final long START = 34;
  public static final int REP = 3;

  public static long serial_fib(long n)
  {
    if (n < 2)
    {
      return n;
    }
    else
    {
      return serial_fib(n - 1) + serial_fib(n - 2);
    }
  }

  public static class FibTask1 extends Task
  {
    final long n;
    final long[] sum;

    //static Factory<FibTask1> factory = new Factory<FibTask1>() {
    //  public FibTask1 construct(Object... arguments) {
    //    return new FibTask1(Long.class.cast(arguments[0]).longValue(), (long[]) (arguments[1]));
    //  }
    //};

    public FibTask1(long n, long[] sum)
    {
      this.n = n;
      this.sum = sum;
    }

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (n < CUT_OFF)
      {
        sum[0] = serial_fib(n);
      }
      else
      {
        long[] x = new long[1];
        long[] y = new long[1];
        FibTask1 a = allocateChild(FibTask1.class, n - 1, x);
        FibTask1 b = allocateChild(FibTask1.class, n - 2, y);
        // FibTask1 a = allocateChild(factory, n - 1, x);
        // FibTask1 b = allocateChild(factory, n - 2, y);
        setRefCount(3);
        spawn(b);
        spawnAndWaitForAll(a);
        sum[0] = x[0] + y[0];
      }
      return null;
    }

    public static long fib(long n)
        throws InstantiationException, IllegalAccessException
    {
      long[] sum = new long[1];
      FibTask1 a = allocateRoot(FibTask1.class, n, sum);
      // FibTask1 a = allocateRoot(factory, n, sum);
      Task.spawnRootAndWait(a);
      return sum[0];
    }
  }

  public static class FibTask2 extends Task
  {

    public static class Continuation extends Task
    {
      long[] sum;
      long[] x = new long[1];
      long[] y = new long[1];

      @Override
      public Task execute()
      {
        sum[0] = x[0] + y[0];
        return null;
      }

    }

    long n;
    long[] sum;

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (n < CUT_OFF)
      {
        sum[0] = serial_fib(n);
      }
      else
      {
        Continuation c = allocateContinuation(Continuation.class);
        c.sum = sum;
        FibTask2 a = c.allocateChild(FibTask2.class);
        a.n = n - 2;
        a.sum = c.x;
        FibTask2 b = c.allocateChild(FibTask2.class);
        b.n = n - 1;
        b.sum = c.y;
        c.setRefCount(2);
        c.spawn(b);
        c.spawn(a);
      }
      return null;
    }

    public static long fib(long n)
        throws InstantiationException, IllegalAccessException
    {
      long[] sum = new long[1];
      FibTask2 a = allocateRoot(FibTask2.class);
      a.n = n;
      a.sum = sum;
      Task.spawnRootAndWait(a);
      return sum[0];
    }
  }

  public static class FibTask3 extends Task
  {

    public static class Continuation extends Task
    {
      long[] sum;
      long[] x = new long[1];
      long[] y = new long[1];

      @Override
      public Task execute()
      {
        sum[0] = x[0] + y[0];
        return null;
      }

    }

    long n;
    long[] sum;

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (n < CUT_OFF)
      {
        sum[0] = serial_fib(n);
      }
      else
      {
        Continuation c = allocateContinuation(Continuation.class);
        c.sum = sum;
        FibTask3 a = c.allocateChild(FibTask3.class);
        a.n = n - 2;
        a.sum = c.x;
        FibTask3 b = c.allocateChild(FibTask3.class);
        b.n = n - 1;
        b.sum = c.y;
        c.setRefCount(2);
        c.spawn(b);
        return a;
      }
      return null;
    }

    public static long fib(long n)
        throws InstantiationException, IllegalAccessException
    {
      long[] sum = new long[1];
      FibTask3 a = allocateRoot(FibTask3.class);
      a.n = n;
      a.sum = sum;
      Task.spawnRootAndWait(a);
      return sum[0];
    }
  }

  public static class FibTask4 extends Task
  {

    public static class Continuation extends Task
    {
      long[] sum;
      long[] x = new long[1];
      long[] y = new long[1];

      @Override
      public Task execute()
      {
        sum[0] = x[0] + y[0];
        return null;
      }

    }

    long n;
    long[] sum;

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (n < CUT_OFF)
      {
        sum[0] = serial_fib(n);
      }
      else
      {
        Continuation c = allocateContinuation(Continuation.class);
        c.sum = sum;
        FibTask4 b = c.allocateChild(FibTask4.class);
        b.n = n - 1;
        b.sum = c.y;
        recycleAsChildOf(c);
        n -= 2;
        sum = c.x;
        c.setRefCount(2);
        c.spawn(b);
        return this;
      }
      return null;
    }

    public static long fib(long n)
        throws InstantiationException, IllegalAccessException
    {
      long[] sum = new long[1];
      FibTask4 a = allocateRoot(FibTask4.class);
      a.n = n;
      a.sum = sum;
      Task.spawnRootAndWait(a);
      return sum[0];
    }
  }

  public static class FibTask5 extends Task
  {
    long n;
    long[] sum;
    long[] x;
    long[] y;

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (n < CUT_OFF)
      {
        sum[0] = serial_fib(n);
      }
      else if (x != null && y != null)
      {
        sum[0] = x[0] + y[0];
      }
      else
      {
        x = new long[1];
        y = new long[1];

        recycleAsContinuation();
        FibTask5 a = allocateChild(FibTask5.class);
        FibTask5 b = allocateChild(FibTask5.class);
        a.n = n - 1;
        a.sum = x;
        b.n = n - 2;
        b.sum = y;
        setRefCount(2);
        spawn(b);
        return a;
      }
      return null;
    }

    public static long fib(long n)
        throws InstantiationException, IllegalAccessException
    {
      long[] sum = new long[1];
      FibTask5 a = allocateRoot(FibTask5.class);
      a.n = n;
      a.sum = sum;
      Task.spawnRootAndWait(a);
      return sum[0];
    }
  }

  public static class FibTask6 extends Task
  {

    static Factory<FibTask6> factory = new Factory<FibTask6>()
    {
      public FibTask6 construct(Object... arguments)
      {
        return new FibTask6();
      }
    };

    long n;
    long[] sum;
    long[] x;
    long[] y;

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (n < CUT_OFF)
      {
        sum[0] = serial_fib(n);
      }
      else if (x != null && y != null)
      {
        sum[0] = x[0] + y[0];
      }
      else
      {
        x = new long[1];
        y = new long[1];

        recycleAsContinuation();
        FibTask6 a = allocateChild(factory);
        FibTask6 b = allocateChild(factory);
        a.n = n - 1;
        a.sum = x;
        b.n = n - 2;
        b.sum = y;
        setRefCount(2);
        spawn(b);
        return a;
      }
      return null;
    }

    public static long fib(long n)
        throws InstantiationException, IllegalAccessException
    {
      long[] sum = new long[1];
      FibTask6 a = allocateRoot(factory);
      a.n = n;
      a.sum = sum;
      Task.spawnRootAndWait(a);
      return sum[0];
    }
  }

  public static class FibTask7 extends Task
  {

    static Factory<FibTask7> factory = new Factory<FibTask7>()
    {
      public FibTask7 construct(Object... arguments)
      {
        return new FibTask7((Long) arguments[0], (long[]) arguments[1]);
      }
    };

    private FibTask7(Long n, long[] sum)
    {
      this.n = n.longValue();
      this.sum = sum;
    }

    final long n;
    final long[] sum;
    long[] x;
    long[] y;

    @Override
    public Task execute()
        throws InstantiationException, IllegalAccessException
    {
      if (n < CUT_OFF)
      {
        sum[0] = serial_fib(n);
      }
      else if (x != null && y != null)
      {
        sum[0] = x[0] + y[0];
      }
      else
      {
        x = new long[1];
        y = new long[1];

        recycleAsContinuation();
        FibTask7 a = allocateChild(factory, n - 1, x);
        FibTask7 b = allocateChild(factory, n - 2, y);
        setRefCount(2);
        spawn(b);
        return a;
      }
      return null;
    }

    public static long fib(long n)
        throws InstantiationException, IllegalAccessException
    {
      long[] sum = new long[1];
      FibTask7 a = allocateRoot(factory, n, sum);
      Task.spawnRootAndWait(a);
      return sum[0];
    }
  }

  @Test
  public void testFibTask1()
      throws InstantiationException, IllegalAccessException
  {
    TaskScheduler init = new TaskScheduler();
    for (int i = 0; i < REP; ++i)
    {
      long t = -System.currentTimeMillis();
      long res = FibTask1.fib(START);
      t += System.currentTimeMillis();
      System.out.println("FibTask1.fib(" + START + ") == " + res + " in " + t + "ms");
    }
    init.terminate();
  }

  @Test
  public void testFibTask2()
      throws InstantiationException, IllegalAccessException
  {
    TaskScheduler init = new TaskScheduler();
    for (int i = 0; i < REP; ++i)
    {
      long t = -System.currentTimeMillis();
      long res = FibTask2.fib(START);
      t += System.currentTimeMillis();
      System.out.println("FibTask2.fib(" + START + ") == " + res + " in " + t + "ms");
    }
    init.terminate();
  }

  @Test
  public void testFibTask3()
      throws InstantiationException, IllegalAccessException
  {
    TaskScheduler init = new TaskScheduler();
    for (int i = 0; i < REP; ++i)
    {
      long t = -System.currentTimeMillis();
      long res = FibTask3.fib(START);
      t += System.currentTimeMillis();
      System.out.println("FibTask3.fib(" + START + ") == " + res + " in " + t + "ms");
    }
    init.terminate();
  }

  @Test
  public void testFibTask4()
      throws InstantiationException, IllegalAccessException
  {
    TaskScheduler init = new TaskScheduler();
    for (int i = 0; i < REP; ++i)
    {
      long t = -System.currentTimeMillis();
      long res = FibTask4.fib(START);
      t += System.currentTimeMillis();
      System.out.println("FibTask4.fib(" + START + ") == " + res + " in " + t + "ms");
    }
    init.terminate();
  }

  @Test
  public void testFibTask5()
      throws InstantiationException, IllegalAccessException
  {
    TaskScheduler init = new TaskScheduler();
    for (int i = 0; i < REP; ++i)
    {
      long t = -System.currentTimeMillis();
      long res = FibTask5.fib(START);
      t += System.currentTimeMillis();
      System.out.println("FibTask5.fib(" + START + ") == " + res + " in " + t + "ms");
    }
    init.terminate();
  }

  @Test
  public void testFibTask6()
      throws InstantiationException, IllegalAccessException
  {
    TaskScheduler init = new TaskScheduler();
    for (int i = 0; i < REP; ++i)
    {
      long t = -System.currentTimeMillis();
      long res = FibTask6.fib(START);
      t += System.currentTimeMillis();
      System.out.println("FibTask6.fib(" + START + ") == " + res + " in " + t + "ms");
    }
    init.terminate();
  }

  @Test
  public void testFibTask7()
      throws InstantiationException, IllegalAccessException
  {
    TaskScheduler init = new TaskScheduler();
    for (int i = 0; i < REP; ++i)
    {
      long t = -System.currentTimeMillis();
      long res = FibTask7.fib(START);
      t += System.currentTimeMillis();
      System.out.println("FibTask7.fib(" + START + ") == " + res + " in " + t + "ms");
    }
    init.terminate();
  }

}

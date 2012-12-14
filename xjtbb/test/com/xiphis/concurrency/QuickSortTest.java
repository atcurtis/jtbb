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

import com.xiphis.concurrent.Parallel;
import com.xiphis.concurrent.TaskScheduler;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * @author atcurtis
 */
public class QuickSortTest
{
  private final Logger LOG = Logger.getLogger("Test");

  private static TaskScheduler init = null;

  private static final int DATA_SIZE = 6553600;
  private static Double[] unsortedData;

  private static long parallelTime;
  private static long serialTime;

  @BeforeTest
  protected void setUp()
      throws Exception
  {
    if (init == null)
    {
      init = new TaskScheduler();

      System.out.println("Generating random numbers.");
      unsortedData = new Double[DATA_SIZE];
      for (int i = 0; i < unsortedData.length; ++i)
      {
        unsortedData[i] = (Math.random() - 0.5) * 1.0e10;
      }
      System.out.println("Finished generating random numbers.");
    }
    parallelTime = serialTime = 0;
  }

  @AfterTest
  protected void cleanup()
      throws Exception
  {
    Thread.sleep(100L);
  }

  @Test
  public void test0ThreadedSort()
  {
    System.gc();
    Double[] data = unsortedData.clone();
    long time = -System.nanoTime();
    Parallel.parallelSort(data);
    time += System.nanoTime();
    System.out.printf("Parallel.parallelSort() took %d ns\n", time);
    //parallelTime += time;
  }

  @Test
  public void test0SystemSort()
  {
    System.gc();
    Double[] data = unsortedData.clone();
    long time = -System.nanoTime();
    Arrays.sort(data);
    time += System.nanoTime();
    System.out.printf("Arrays.sort() took %d ns\n", time);
    //serialTime += time;
  }

  @Test
  public void test1ThreadedSort()
  {
    System.gc();
    Double[] data = unsortedData.clone();
    long time = -System.nanoTime();
    Parallel.parallelSort(data);
    time += System.nanoTime();
    System.out.printf("Parallel.parallelSort() took %d ns\n", time);
    parallelTime += time;
  }

  @Test
  public void test1SystemSort()
  {
    System.gc();
    Double[] data = unsortedData.clone();
    long time = -System.nanoTime();
    Arrays.sort(data);
    time += System.nanoTime();
    System.out.printf("Arrays.sort() took %d ns\n", time);
    serialTime += time;
  }

  @Test
  public void test2ThreadedSort()
  {
    System.gc();
    Double[] data = unsortedData.clone();
    long time = -System.nanoTime();
    Parallel.parallelSort(data);
    time += System.nanoTime();
    System.out.printf("Parallel.parallelSort() took %d ns\n", time);
    parallelTime += time;
  }

  @Test
  public void test2SystemSort()
  {
    System.gc();
    Double[] data = unsortedData.clone();
    long time = -System.nanoTime();
    Arrays.sort(data);
    time += System.nanoTime();
    System.out.printf("Arrays.sort() took %d ns\n", time);
    serialTime += time;
  }

  @Test
  public void test3ThreadedSort()
  {
    System.gc();
    Double[] data = unsortedData.clone();
    long time = -System.nanoTime();
    Parallel.parallelSort(data);
    time += System.nanoTime();
    System.out.printf("Parallel.parallelSort() took %d ns\n", time);
    parallelTime += time;
  }

  @Test
  public void test3SystemSort()
  {
    System.gc();
    Double[] data = unsortedData.clone();
    long time = -System.nanoTime();
    Arrays.sort(data);
    time += System.nanoTime();
    System.out.printf("Arrays.sort() took %d ns\n", time);
    serialTime += time;
  }

  @Test
  public void testResults()
  {
    System.out.println("Done .. speed up is " + ((serialTime * 100) / parallelTime) + "%");
  }

}

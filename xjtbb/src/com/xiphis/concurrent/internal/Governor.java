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
package com.xiphis.concurrent.internal;

import com.xiphis.concurrent.TaskScheduler;
import com.xiphis.concurrent.serial.SimpleScheduler;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

public class Governor
{
  private final static Logger LOG = TBB.LOG;

  public static String _defaultScheduler = com.xiphis.concurrent.parallel.ParallelScheduler.class.getName();
  static SchedulerInitializer _initScheduler;
  static final ThreadLocal<Scheduler> _theTLS = new ThreadLocal<Scheduler>();

  /**
   * Obtain the thread local instance of TBB scheduler.
   * <p/>
   * Returns NULL if this is the first time the thread has requested a
   * scheduler. It's the client's responsibility to check for the NULL,
   * because in many contexts, we can prove that it cannot be NULL.
   *
   * @return
   */
  public static Scheduler localScheduler()
  {
    if (TBB.USE_ASSERT) assert _theTLS.get() != null : "thread did not activate a task_scheduler_init object?";
    return _theTLS.get();
  }

  /**
   * Create a thread local instance of TBB scheduler on demand.
   *
   * @return
   */
  public static Scheduler localSchedulerWithAutoInit()
  {
    Scheduler s = _theTLS.get();
    if (TBB.TASK_SCHEDULER_AUTO_INIT && s == null)
    {
      TBB.runOneTimeInitialization();
      try
      {
        s = Scheduler.createMasterScheduler(TaskScheduler.defaultThreads());
      }
      catch (InstantiationException e)
      {
        LOG.throwing(Governor.class.getName(), "localSchedulerWithAutoInit", e);
        throw new RuntimeException(e);
      }
      catch (IllegalAccessException e)
      {
        LOG.throwing(Governor.class.getName(), "localSchedulerWithAutoInit", e);
        throw new RuntimeException(e);
      }
      if (s != null)
      {
        s._autoInitialized = true;
        LOG.fine("Scheduler was initialized automatically");
      }
    }
    if (TBB.USE_ASSERT) assert s != null : "somehow a scheduler object was not created?";
    return s;
  }

  public static void signOn(Scheduler s)
  {
    if (TBB.USE_ASSERT) assert s._registered == false : "Assertion failed: s._registered == false";
    s._registered = true;
    _theTLS.set(s);
  }

  static void signOff(final Scheduler s)
  {
    if (s._registered)
    {
      if (TBB.USE_ASSERT) assert _theTLS.get() == s : "attempt to unregister a wrong scheduler instance";

      _theTLS.set(null);
      s._registered = false;
    }
  }

  private static Scheduler failsafeCreateScheduler(Object[] args)
      throws InstantiationException, IllegalAccessException
  {
    _initScheduler = new SchedulerInitializer()
    {
      public Scheduler createScheduler(Object[] a)
          throws InstantiationException, IllegalAccessException
      {
        return new SimpleScheduler();
      }
    };
    return _initScheduler.createScheduler(args);
  }

  @SuppressWarnings("unchecked")
  private static Scheduler instantiate(Class<? extends Scheduler> cls, Object[] args)
      throws InstantiationException,
             IllegalAccessException
  {
    if (args != null && args.length > 0)
    {
      for (Constructor<?> c : cls.getConstructors())
      {
        if (c.getParameterTypes().length < args.length && !c.isVarArgs())
        {
          continue;
        }
        try
        {
          return ((Constructor<? extends Scheduler>) c).newInstance(args);
        }
        catch (IllegalArgumentException e)
        {
          continue;
        }
        catch (InvocationTargetException e)
        {
          LOG.throwing(Governor.class.getName(), "instantiate", e.getCause());
          throw new RuntimeException(e.getCause());
        }
      }
    }
    return cls.newInstance();
  }

  private static Scheduler initialCreateScheduler(Object[] args)
      throws InstantiationException, IllegalAccessException
  {
    try
    {
      String scheduler_class_name = System.getProperty(Scheduler.class.getCanonicalName(), _defaultScheduler);
      Class<?> found_cls = Class.forName(scheduler_class_name);
      final Class<? extends Scheduler> scheduler_class = found_cls.asSubclass(Scheduler.class);
      Scheduler result = scheduler_class.newInstance();
      _initScheduler = new SchedulerInitializer()
      {
        public Scheduler createScheduler(Object[] a)
            throws InstantiationException, IllegalAccessException
        {
          return instantiate(scheduler_class, a);
        }
      };
      return result;
    }
    catch (Throwable ex)
    {
      return failsafeCreateScheduler(args);
    }
  }

  static void initialize()
  {
    _initScheduler = new SchedulerInitializer()
    {
      public Scheduler createScheduler(Object[] a)
          throws InstantiationException, IllegalAccessException
      {
        return initialCreateScheduler(a);
      }
    };
  }

  public static int numberOfWorkersInArena()
  {
    return localScheduler().numberOfWorkers();
  }

  interface SchedulerInitializer
  {
    Scheduler createScheduler(Object[] args)
        throws InstantiationException, IllegalAccessException;
  }
}

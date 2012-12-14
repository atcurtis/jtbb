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
package com.xiphis.concurrent.parallel;

import com.xiphis.concurrent.Task;
import com.xiphis.concurrent.internal.MailOutbox;
import com.xiphis.concurrent.internal.TBB;

import java.util.logging.Logger;

public final class Arena
{
  private static final Logger LOG = TBB.LOG;

  // Use unique id for "busy" in order to avoid ABA problems.
  private final Snapshot SNAPSHOT_BUSY = new Snapshot(this);
  ArenaPrefix _arenaPrefix;
  ArenaSlot[] _slot;

  Arena(int number_of_slots, int number_of_workers)
  {
    _arenaPrefix = new ArenaPrefix(number_of_slots, number_of_workers);

    // Allocate the _workerList
    WorkerDescriptor[] w = prefix()._workerList = new WorkerDescriptor[number_of_workers];
    for (int i = 0; i < number_of_workers; ++i)
    {
      w[i] = new WorkerDescriptor();
    }

    ArenaSlot[] s = _slot = new ArenaSlot[number_of_slots];
    for (int i = 0; i < number_of_slots; ++i)
    {
      s[i] = new ArenaSlot();
    }

    // Verify that earlier memset initialized the mailboxes.
    // for( int j=1; j<=_numberOfSlots; ++j ) {
    // mailbox(j).assertIsInitialized();
    // }

    int k;
    // Mark each worker _slot as locked and unused
    for (k = 0; k < number_of_workers; ++k)
    {
      // All slots are set to null meaning that they are free
      w[k]._arena = this;
      // ITT_SYNC_CREATE(a->_slot + k, SyncType_Scheduler,
      // SyncObj_WorkerTaskPool);
      // ITT_SYNC_CREATE(&w[k]._scheduler, SyncType_Scheduler,
      // SyncObj_WorkerLifeCycleMgmt);
      // ITT_SYNC_CREATE(&a->mailbox(k+1), SyncType_Scheduler,
      // SyncObj_Mailbox);
    }
    // Mark rest of slots as unused
    // for( ; k<_numberOfSlots; ++k ) {
    // ITT_SYNC_CREATE(a->_slot + k, SyncType_Scheduler,
    // SyncObj_MasterTaskPool);
    // ITT_SYNC_CREATE(&a->mailbox(k+1), SyncType_Scheduler,
    // SyncObj_Mailbox);
    // }
  }

  ArenaPrefix prefix()
  {
    return _arenaPrefix;
  }

  MailOutbox mailbox(int id)
  {
    if (TBB.USE_ASSERT && !(0 < id))
    {
      AssertionError e = new AssertionError("id must be positive integer");
      LOG.throwing(Arena.class.getName(), "mailbox", e);
      throw e;
    }
    if (TBB.USE_ASSERT && !(id <= prefix()._numberOfSlots))
    {
      AssertionError e = new AssertionError("id out of bounds");
      LOG.throwing(Arena.class.getName(), "internalWaitForAll", e);
      throw e;
    }
    return prefix()._mailboxList[id];
  }

  public void markPoolFull()
  {
    // Double-check idiom that is deliberately sloppy about memory fences.
    // Technically, to avoid missed wakeups, there should be a full memory fence between the point we
    // released the task pool (i.e. spawned task) and read the gate's state. However, adding such a
    // fence might hurt overall performance more than it helps, because the fence would be executed
    // on every task pool release, even when stealing does not occur. Since TBB allows parallelism,
    // but never promises parallelism, the missed wakeup is not a correctness problem.
    Snapshot snapshot = prefix()._gate.getState();
    if (snapshot != Snapshot.SNAPSHOT_FULL)
    {
      prefix()._gate.tryUpdate(Snapshot.SNAPSHOT_FULL, Snapshot.SNAPSHOT_PERMANENTLY_OPEN, true);
    }
  }

  boolean waitWhilePoolIsEmpty()
  {
    for (; ; )
    {
      Snapshot snapshot = prefix()._gate.getState();
      switch (snapshot.state)
      {
      case SNAPSHOT_EMPTY:
        prefix()._gate.await();
        return true;
      case SNAPSHOT_FULL:
      {
        // Request permission to take snapshot
        prefix()._gate.tryUpdate(SNAPSHOT_BUSY, Snapshot.SNAPSHOT_FULL);
        if (prefix()._gate.getState() == SNAPSHOT_BUSY)
        {
          // Got permission. Take the snapshot.
          int n = prefix()._limit;
          int k;
          for (k = 0; k < n; ++k)
          {
            if (_slot[k]._taskPool.get() != ArenaSlot.EMPTY_TASK_POOL && _slot[k].head < _slot[k].tail)
            {
              break;
            }
          }
          // Test and test-and-set.
          if (prefix()._gate.getState() == SNAPSHOT_BUSY)
          {
            if (k >= n)
            {
              prefix()._gate.tryUpdate(Snapshot.SNAPSHOT_EMPTY, SNAPSHOT_BUSY);
              continue;
            }
            else
            {
              prefix()._gate.tryUpdate(Snapshot.SNAPSHOT_FULL, SNAPSHOT_BUSY);
            }
          }
        }
        return false;
      }
      default:
        // Another thread is taking a snapshot or gate is permanently open.
        return false;
      }
    }
  }

  void terminateWorkers()
  {
    int n = prefix().number_of_workers;
    if (TBB.USE_ASSERT) assert n >= 0 : "negative number of workers; casting error?";
    for (int i = n; --i >= 0; )
    {
      WorkerDescriptor w = prefix()._workerList[i];
      if (w._scheduler.getReference() != null || !w._scheduler.compareAndSet(null, null, false, true))
      {
        // Worker published itself. Tell worker to quit.
        // ITT_NOTIFY(sync_acquired, &w._scheduler);
        Task t = w._scheduler.getReference().dummyTask();
        // ITT_NOTIFY(sync_releasing, &t->prefix()._refCount);
        ParallelScheduler.setTaskRefCount(t, 1);
      }
      else
      {
        // Worker did not publish itself yet, and we have set w._scheduler to -1,
        // which tells the worker that it should never publish itself.
      }
    }
    // Permanently wake up sleeping workers
    prefix()._gate.tryUpdate(Snapshot.SNAPSHOT_PERMANENTLY_OPEN, Snapshot.SNAPSHOT_PERMANENTLY_OPEN, true);
    removeGcReference();

  }

  int workersTaskNodeCount()
  {
    int result = 0;
    for (int i = 0; i < prefix().number_of_workers; ++i)
    {
      com.xiphis.concurrent.internal.Scheduler s = prefix()._workerList[i]._scheduler.getReference();
      if (s != null)
      {
        result += s.getTaskNodeCount();
      }
    }
    return result;
  }

  void removeGcReference()
  {
    if (--prefix()._gcRefCount == 0)
    {
      freeArena();
    }
  }

  /**
   * Drain mailboxes
   */
  void freeArena()
  {
    // TODO: each scheduler should plug-and-drain its own mailbox when it terminates.
    int drain_count = 0;
    for (int i = 1; i <= prefix()._numberOfSlots; ++i)
    {
      drain_count += mailbox(i).drain();
    }
    prefix()._taskNodeCount -= drain_count;
    _arenaPrefix = null;
  }
}

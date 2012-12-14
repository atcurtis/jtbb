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

import com.xiphis.concurrent.internal.Gate;
import com.xiphis.concurrent.internal.MailOutbox;

public class ArenaPrefix
{

  public final int number_of_workers;
  public final int _numberOfSlots;
  public final Gate<Snapshot> _gate;
  public int _numberOfMasters;
  public int _limit;
  public int _gcRefCount;
  public int _taskNodeCount;
  public WorkerDescriptor[] _workerList;
  public MailOutbox[] _mailboxList;

  public ArenaPrefix(int number_of_slots, int number_of_workers)
  {
    this._numberOfMasters = 1;
    this.number_of_workers = number_of_workers;
    this._numberOfSlots = number_of_slots;
    this._limit = number_of_workers;
    this._gate = new Gate<Snapshot>(Snapshot.SNAPSHOT_EMPTY);
    this._mailboxList = new MailOutbox[number_of_slots + 1];
    for (int i = 0; i <= number_of_slots; ++i)
    {
      this._mailboxList[i] = new MailOutbox();
    }
  }
}

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

public class Snapshot
{

  public enum State
  {
    SNAPSHOT_EMPTY, SNAPSHOT_FULL, SNAPSHOT_PERMANENTLY_OPEN, SNAPSHOT_SCHEDULER
  }

  public final static Snapshot SNAPSHOT_EMPTY = new Snapshot(State.SNAPSHOT_EMPTY);
  public final static Snapshot SNAPSHOT_FULL = new Snapshot(State.SNAPSHOT_FULL);
  public final static Snapshot SNAPSHOT_PERMANENTLY_OPEN = new Snapshot(State.SNAPSHOT_PERMANENTLY_OPEN);

  public final State state;
  public final Arena value;

  private Snapshot(State state)
  {
    this.state = state;
    this.value = null;
  }

  public Snapshot(Arena value)
  {
    this.state = State.SNAPSHOT_SCHEDULER;
    this.value = value;
  }

  private boolean test(Snapshot foo)
  {
    return foo != null && state == foo.state && value == foo.value;
  }

  @Override
  public boolean equals(Object foo)
  {
    return super.equals(foo) || test(Snapshot.class.cast(foo));
  }

}

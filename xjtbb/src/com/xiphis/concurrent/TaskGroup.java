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

import com.xiphis.concurrent.internal.TBB;
import com.xiphis.concurrent.internal.TaskGroupBase;

public class TaskGroup extends TaskGroupBase
{
  public TaskGroup()
  {
    super(TaskGroupContext.concurrent_wait);
  }

  @Override
  protected void finalize()
      throws Throwable
  {
    try
    {
      if (my_root != null)
      {
        destroy();
      }
    }
    finally
    {
      super.finalize();
    }
  }

  public final void destroy()
  {
    try
    {
      if (TBB.USE_ASSERT) assert my_root.ref_count() != 0;
      if (my_root.ref_count() > 1)
      {
        my_root.waitForAll();
      }
      owner().destroy(my_root);
    }
    finally
    {
      owner().destroy(my_root);
      my_root = null;
    }
  }

  @Override
  public void run(Runnable f)
  {
    internal_run(f);
  }

  public Status runAndWait(Runnable f)
  {
    return internal_run_and_wait(f);
  }

}

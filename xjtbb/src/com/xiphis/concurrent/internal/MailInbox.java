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


import java.util.logging.Logger;

public final class MailInbox
{
  private static final Logger LOG = TBB.LOG;

  //! Corresponding sink where mail that we receive will be put.
  private MailOutbox _putter;

  /**
   * Construct unattached inbox
   */
  public MailInbox()
  {
    _putter = null;
  }

  /**
   * Attach inbox to a corresponding outbox.
   */
  public void attach(MailOutbox putter)
  {
    if (TBB.USE_ASSERT) assert _putter == null : "already attached";
    _putter = putter;
  }

  /**
   * Detach inbox from its outbox
   */
  public void detach()
  {
    if (TBB.USE_ASSERT) assert _putter != null : "not attached";
    _putter = null;
  }

  /**
   * Get next piece of mail, or NULL if mailbox is empty.
   */
  public TaskProxy pop()
  {
    return _putter.internalPop();
  }

  public void setIsIdle(boolean value)
  {
    if (_putter != null)
    {
      if (TBB.USE_ASSERT) assert _putter._is_idle == !value;
      _putter._is_idle = value;
    }
  }

  /**
   * Get pointer to corresponding outbox used.
   *
   * @return
   */
  public MailOutbox outbox()
  {
    return _putter;
  }

}

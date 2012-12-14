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

public class Statistics
{

  public int execute_count;
  public int proxy_bypass_count;
  public int proxy_execute_count;
  public int proxy_steal_count;
  public int mail_received_count;
  public int reflect_construct_count;
  public int reflect_newinstance_count;
  public int steal_count;
  public long allocate_overhead;
  public long spawn_overhead;
  public long wait_for_all_overhead;
  public int current_active;

  public synchronized void record(Statistics source)
  {
    execute_count += source.execute_count;
    proxy_bypass_count += source.proxy_bypass_count;
    proxy_execute_count += source.proxy_execute_count;
    proxy_steal_count += source.proxy_steal_count;
    mail_received_count += source.mail_received_count;
    reflect_construct_count += source.reflect_construct_count;
    reflect_newinstance_count += source.reflect_newinstance_count;
    steal_count += source.steal_count;
    allocate_overhead += source.allocate_overhead;
    spawn_overhead += source.spawn_overhead;
    wait_for_all_overhead += source.wait_for_all_overhead;
  }

  @Override
  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("Statistics:");
    sb.append("\nexecute_count=");
    sb.append(execute_count);
    sb.append("\nproxy_bypass_count=");
    sb.append(proxy_bypass_count);
    sb.append("\nproxy_execute_count=");
    sb.append(proxy_execute_count);
    sb.append("\nproxy_steal_count=");
    sb.append(proxy_steal_count);
    sb.append("\nmail_received_count=");
    sb.append(mail_received_count);
    sb.append("\nreflect_construct_count=");
    sb.append(reflect_construct_count);
    sb.append("\nreflect_newinstance_count=");
    sb.append(reflect_newinstance_count);
    sb.append("\nsteal_count=");
    sb.append(steal_count);
    sb.append("\nallocate_overhead=");
    sb.append(allocate_overhead);
    sb.append("ms\nspawn_overhead=");
    sb.append(spawn_overhead);
    sb.append("ms\nwait_for_all_overhead=");
    sb.append(wait_for_all_overhead);
    sb.append("ms");
    return sb.toString();
  }
}

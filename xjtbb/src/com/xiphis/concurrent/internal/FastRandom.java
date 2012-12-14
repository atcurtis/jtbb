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

/**
 * A fast random number generator.
 * <p/>
 * Uses linear congruential method.
 *
 * @author atcurtis
 */
public final class FastRandom
{

  private final static int _primes[] = {0x9e3779b1, 0xffe6cc59, 0x2109f6dd, 0x43977ab5, 0xba5703f5, 0xb495a877, 0xe1626741,
      0x79695e6b, 0xbc98c09f, 0xd5bee2b3, 0x287488f9, 0x3af18231, 0x9677cd4d, 0xbe3a6929, 0xadc6a877, 0xdcf0674b, 0xbe4d6fe9,
      0x5f15e201, 0x99afc3fd, 0xf3f16801, 0xe222cfff, 0x24ba5fdb, 0x0620452d, 0x79f149e3, 0xc8b93f49, 0x972702cd, 0xb07dd827,
      0x6c97d5ed, 0x085a3d61, 0x46eb5ea7, 0x3d9910ed, 0x2e687b5b, 0x29609227, 0x6eb081f1, 0x0954c4e1, 0x9d114db9, 0x542acfa9,
      0xb3e6bd7b, 0x0742d917, 0xe9f3ffa7, 0x54581edb, 0xf2480f45, 0x0bb9288f, 0xef1affc7, 0x85fa0ca7, 0x3ccc14db, 0xe6baf34b,
      0x343377f7, 0x5ca19031, 0xe6d9293b, 0xf0a9f391, 0x5d2e980b, 0xfc411073, 0xc3749363, 0xb892d829, 0x3549366b, 0x629750ad,
      0xb98294e5, 0x892d9483, 0xc235baf3, 0x3d2402a3, 0x6bdef3c9, 0xbec333cd, 0x40c9520f};
  private final int _a;
  private int _x;

  /**
   * Construct _a random number generator.
   *
   * @param seed
   */
  public FastRandom(int seed)
  {
    _x = seed;
    _a = _primes[(0x7ffffff & seed) % _primes.length];
  }

  /**
   * Get _a random number.
   *
   * @return
   */
  public int get()
  {
    int r = _x >> 16;
    _x = _x * _a + 1;
    return r & 0xffff;
  }
}

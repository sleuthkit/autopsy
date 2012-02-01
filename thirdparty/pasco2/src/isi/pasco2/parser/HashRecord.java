/*
 * Copyright 2006 Bradley Schatz. All rights reserved.
 *
 * This file is part of pasco2, the next generation Internet Explorer cache
 * and history record parser.
 *
 * pasco2 is free software; you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * pasco2 is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with pasco2; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */


package isi.pasco2.parser;

import java.io.IOException;

public class HashRecord {
  int flags;
  int recordOffset;
  Allocator allocator;
  
  public HashRecord(Allocator allocator, int flags, int offset) {
    this.flags = flags;
    this.recordOffset = offset;
    this.allocator = allocator;
  }
  
  public boolean isValid() {
    return (flags & 0x0FF) != 0x03 && recordOffset != 0;
  }
  public boolean containsUnallocatedMemorySignature() {
    return recordOffset == 0xBADF00D;
  }   
  public boolean isAllocated() throws IOException {
    return allocator.isRecordAllocated(recordOffset);
  }
}

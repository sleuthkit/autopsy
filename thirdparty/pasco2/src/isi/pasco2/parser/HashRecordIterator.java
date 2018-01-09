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


import isi.pasco2.io.IndexFile;

import java.io.IOException;

public class HashRecordIterator {
  int maxBounds;
  int offset;
  HashBlock hashBlock;
  IndexFile file;
  Allocator allocator;
  
  public HashRecordIterator(HashBlock block, IndexFile file, Allocator allocator) throws IOException {
     this.hashBlock = block;
     this.file = file;
     this.allocator = allocator;
     maxBounds = hashBlock.getOffset() + hashBlock.size();
     offset = hashBlock.getOffset() + 16;
  }
  
  public boolean hasNext() {
    if (offset < maxBounds) {
      return true;
    }
    return false;
  }
  
  public HashRecord next() throws IOException {
    int flags = file.readIntAtOffset(offset);
    int recordOffset = file.readIntAtOffset( offset + 4);
    HashRecord rec = new HashRecord(allocator, flags, recordOffset);
    offset += 8;
    return rec;
  }
}

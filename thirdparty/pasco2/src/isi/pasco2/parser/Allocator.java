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

import com.sun.org.apache.xalan.internal.xsltc.dom.BitArray;


public class Allocator {
  static int allocatorBitMapOffset = 0x250;
  IEIndexFileParser parser;
  IndexFile file;
  public Allocator(IEIndexFileParser parser) {
    this.parser = parser;
    this.file = parser.indexFile;
  }
  
  public boolean isRecordAllocated(long recordAddress)
      throws IOException {
    long bitMapIndex = ((recordAddress - parser.getHashOffset()) / 0x80);
    long dwordOffset = bitMapIndex / 32;
    long bitOffset = bitMapIndex % 32;

    file.seek(allocatorBitMapOffset + dwordOffset * 4);

    int[] bitmap = { file.readLittleEndianInt() };
    BitArray bits = new BitArray(32, bitmap);

    return bits.getBit((int) bitOffset);
  }
}

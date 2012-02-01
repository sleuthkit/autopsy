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


package isi.pasco2;


import isi.pasco2.handler.CountingCacheHandler;
import isi.pasco2.io.FastReadIndexFile;
import isi.pasco2.io.IndexFile;
import isi.pasco2.parser.IEHistoryFileParser;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.sun.org.apache.xalan.internal.xsltc.dom.BitArray;

public class TestAllocationLayer extends TestCase {
  
    public static TestSuite suite() {
        return new TestSuite(TestAllocationLayer.class);
    }
   
 

  public void testHistoryFile() {
    try {
      String f = "D:\\Documents and Settings\\bschatz\\Local Settings\\History\\History.IE5\\index.dat";
      IndexFile fr = new FastReadIndexFile(f, "r");
      CountingCacheHandler handler = new CountingCacheHandler();
      IEHistoryFileParser parser = new IEHistoryFileParser(f, fr);
      
      int allocatorBitMapOffset = 0x250;
      int bitMapIndex = 0;
      int storageStart = 0x4000;
      parser.primaryHashOffset = storageStart;
      int address = 0;
      while (address != storageStart) {
        fr.seek(allocatorBitMapOffset + bitMapIndex*4);

        int[] bitmap = {fr.readLittleEndianInt()};
        BitArray bits = new BitArray(32, bitmap);
 
        for (int i = 0 ; i < 32 ; i++ ){
          address = (bitMapIndex*32+i) * 0x80 + storageStart;
          fr.seek(address);
          int dword = fr.readLittleEndianInt();
  
          System.out.println((bitMapIndex*32+i) + " " + Integer.toHexString(dword) + " " + Integer.toHexString(address));
          assertTrue(bits.getBit(i) == parser.getAllocator().isRecordAllocated(address));

        }
        bitMapIndex += 1;
      }
    } catch (Exception e) {
    }
  }

  public void testCacheFile() {
    try {
      String f = "D:\\mysrc\\squidbro\\IFIP2006\\anon\\source\\minnow.willk.content.index.dat";
      IndexFile fr = new FastReadIndexFile(f, "r");
      CountingCacheHandler handler = new CountingCacheHandler();
      IEHistoryFileParser parser = new IEHistoryFileParser(f, fr);
      
      int allocatorBitMapOffset = 0x250;
      int bitMapIndex = 0;
      int storageStart = 0x5000;
      parser.primaryHashOffset = storageStart;
      int address = 0;
      while (address != storageStart) {
        fr.seek(allocatorBitMapOffset + bitMapIndex*4);

        int[] bitmap = {fr.readLittleEndianInt()};
        BitArray bits = new BitArray(32, bitmap);
 
        for (int i = 0 ; i < 32 ; i++ ){
          address = (bitMapIndex*32+i) * 0x80 + storageStart;
          fr.seek(address);
          int dword = fr.readLittleEndianInt();
  
          System.out.println((bitMapIndex*32+i) + " " + Integer.toHexString(dword) + " " + Integer.toHexString(address));
          assertTrue(bits.getBit(i) == parser.getAllocator().isRecordAllocated(address));

        }
        bitMapIndex += 1;
      }
    } catch (Exception e) {
    }
  }
  
  
}

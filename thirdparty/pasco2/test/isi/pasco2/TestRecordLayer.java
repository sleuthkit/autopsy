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

import isi.pasco2.io.FastReadIndexFile;
import isi.pasco2.io.IndexFile;
import isi.pasco2.parser.IECacheFileParser;
import isi.pasco2.parser.IEHistoryFileParser;
import isi.pasco2.parser.IEIndexFileParser;
import isi.pasco2.parser.ValidRecordIterator;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestRecordLayer extends TestCase {

  public static TestSuite suite() {
    return new TestSuite(TestRecordLayer.class);
  }

  public void testEmptyCacheFile() {
    try {
      String f = "D:\\mysrc\\pasco2\\src\\test\\isrc\\pasco2\\empty.cache.index.dat";
      IndexFile fr = new FastReadIndexFile(f, "r");
      IEIndexFileParser parser = new IECacheFileParser(f, fr);
      parser.parseFile();

      ValidRecordIterator it = parser.getValidRecords();
      int count = 0;
      while (it.hasNext()) {
        count += 1;
        it.next();
      }
      assertEquals(00, count);
    } catch (Exception e) {
      fail();
    }
  }

  public void testInitialCacheFile() {
    try {
      String f = "D:\\mysrc\\pasco2\\src\\test\\isrc\\pasco2\\first.cache.index.dat";
      IndexFile fr = new FastReadIndexFile(f, "r");
      IEIndexFileParser parser = new IECacheFileParser(f, fr);

      ValidRecordIterator it = parser.getValidRecords();
      int count = 0;
      while (it.hasNext()) {
        count += 1;
        it.next();
      }
      assertEquals(30, count);
    } catch (Exception e) {
      fail();
    }
  }

  public void testEmptyHistoryFile() {
    try {
      String f = "D:\\mysrc\\pasco2\\src\\test\\isrc\\pasco2\\empty.history.index.dat";
      IndexFile fr = new FastReadIndexFile(f, "r");
      IEIndexFileParser parser = new IEHistoryFileParser(f, fr);
      parser.parseFile();

      ValidRecordIterator it = parser.getValidRecords();
      int count = 0;
      while (it.hasNext()) {
        count += 1;
        it.next();
      }
      assertEquals(00, count);
    } catch (Exception e) {
      fail();
    }
  }

  public void testInitialHistoryFile() {
    try {
      String f = "D:\\mysrc\\pasco2\\src\\test\\isrc\\pasco2\\first.history.index.dat";
      IndexFile fr = new FastReadIndexFile(f, "r");
      IEIndexFileParser parser = new IEHistoryFileParser(f, fr);

      ValidRecordIterator it = parser.getValidRecords();
      int count = 0;
      while (it.hasNext()) {
        count += 1;
        it.next();
      }
      assertEquals(2, count);
    } catch (Exception e) {
      fail();
    }
  }

}

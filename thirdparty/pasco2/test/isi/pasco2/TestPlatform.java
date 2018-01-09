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

import isi.pasco2.parser.time.FileTime;
import isi.pasco2.platform.FILETIME;
import isi.pasco2.platform.SYSTEMTIME;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import ctypes.java.CDLL;
import ctypes.java.CFunction;
import ctypes.java.CInt;

public class TestPlatform extends TestCase {

  public static TestSuite suite() {
    return new TestSuite(TestPlatform.class);
  }

  public void testSystemTimeAPIs() {
    try {
      CDLL dll = CDLL.LoadLibrary("kernel32.dll");
      CFunction getSystemTimeAsFileTime = dll
          .loadFunction("GetSystemTimeAsFileTime");

      FILETIME ft = new FILETIME();
      Object[] ary = { ft };
      Object o = getSystemTimeAsFileTime.call(null, ary,
          CFunction.FUNCFLAG_STDCALL);
      long lowValue = ft.dwLowDateTime.getValue();
      long highValue = ft.dwHighDateTime.getValue();
      System.out.println(lowValue);
      System.out.println(highValue);
      assertTrue(highValue >= 29767328);
      /*
       * BOOL FileTimeToSystemTime( const FILETIME* lpFileTime, LPSYSTEMTIME
       * lpSystemTime );
       */

      CFunction fileTimeToSystemTime = dll.loadFunction("FileTimeToSystemTime");
      SYSTEMTIME st = new SYSTEMTIME();
      Object[] ary1 = { ft, st };
      o = fileTimeToSystemTime.call(CInt.class, ary1,
          CFunction.FUNCFLAG_STDCALL);
      int year = st.wYear.getValue();
      assertTrue(year >= 2006);

    } catch (Exception e) {
      fail();
    }
  }

  public void testComparingSystemTimesWithOurAlgorithm2() {
    try {
      CDLL dll = CDLL.LoadLibrary("kernel32.dll");
      CFunction getSystemTimeAsFileTime = dll
          .loadFunction("GetSystemTimeAsFileTime");

      int lowValue = 926944912;
      int highValue = 29767328;

      FILETIME ft = new FILETIME();
      ft.dwLowDateTime.setValue(lowValue);
      ft.dwHighDateTime.setValue(highValue);

      CFunction fileTimeToSystemTime = dll.loadFunction("FileTimeToSystemTime");
      SYSTEMTIME st = new SYSTEMTIME();
      Object[] ary1 = { ft, st };

      Object o = fileTimeToSystemTime.call(CInt.class, ary1,
          CFunction.FUNCFLAG_STDCALL);
      assertEquals(2006, st.wYear.getValue());
      assertEquals(2, st.wMonth.getValue());
      assertEquals(21, st.wDay.getValue());
      assertEquals(4, st.wHour.getValue());
      assertEquals(35, st.wMinute.getValue());
      assertEquals(17, st.wSecond.getValue());
      assertEquals(625, st.wMilliseconds.getValue());

      FileTime nonSystemFileTime = new FileTime(lowValue, highValue);
      assertEquals("2006-02-21T04:35:17.625Z", nonSystemFileTime.toXSDString());

    } catch (Exception e) {
      fail();
    }
  }
  
  public void testComparingSystemTimesWithOurAlgorithm() {
    try {
      CDLL dll = CDLL.LoadLibrary("kernel32.dll");
      CFunction getSystemTimeAsFileTime = dll
          .loadFunction("GetSystemTimeAsFileTime");

      String hex = "40B3B13FED2AC001";
      FileTime f = FileTime.parseLittleEndianHex(hex);

      FILETIME ft = new FILETIME();
      ft.dwLowDateTime.setValue((int) f.getLow());
      ft.dwHighDateTime.setValue((int) f.getHigh());

      CFunction fileTimeToSystemTime = dll.loadFunction("FileTimeToSystemTime");
      SYSTEMTIME st = new SYSTEMTIME();
      Object[] ary1 = { ft, st };

      //Sat, 30 September 2000 14:46:43
      Object o = fileTimeToSystemTime.call(CInt.class, ary1,
          CFunction.FUNCFLAG_STDCALL);
      assertEquals(2000, st.wYear.getValue());
      assertEquals(9, st.wMonth.getValue());
      assertEquals(30, st.wDay.getValue());
      assertEquals(14, st.wHour.getValue());
      assertEquals(46, st.wMinute.getValue());
      assertEquals(43, st.wSecond.getValue());
      assertEquals(60, st.wMilliseconds.getValue());


    } catch (Exception e) {
      fail();
    }
  }
  
  public void testZeroFiletime() {
    try {
      CDLL dll = CDLL.LoadLibrary("kernel32.dll");

      FILETIME ft = new FILETIME();
      ft.dwLowDateTime.setValue((int) 0);
      ft.dwHighDateTime.setValue((int) 0);

      CFunction fileTimeToSystemTime = dll.loadFunction("FileTimeToSystemTime");
      CFunction getFileTimeAsSystemTime = dll.loadFunction("SystemTimeToFileTime");
      
      SYSTEMTIME st = new SYSTEMTIME();
      Object[] ary1 = { ft, st };

      //Sat, 30 September 2000 14:46:43
      Object o = fileTimeToSystemTime.call(CInt.class, ary1,
          CFunction.FUNCFLAG_STDCALL);
      assertEquals(1601, st.wYear.getValue());
      assertEquals(1, st.wMonth.getValue());
      assertEquals(1, st.wDay.getValue());
      assertEquals(0, st.wHour.getValue());
      assertEquals(0, st.wMinute.getValue());
      assertEquals(0, st.wSecond.getValue());
      assertEquals(0, st.wMilliseconds.getValue());

      FILETIME zeroFileTime = new FILETIME();
      Object[] a = { st, zeroFileTime };
      Object o1 = getFileTimeAsSystemTime.call(CInt.class, ary1,
          CFunction.FUNCFLAG_STDCALL);
      
      assertEquals(0, zeroFileTime.dwHighDateTime.getValue());
      assertEquals(0, zeroFileTime.dwLowDateTime.getValue());
      
    } catch (Exception e) {
      fail();
    }
  }
  
  /**
   * This test validates the value of the constant called skewFILETIME2timetEpochs 
   * used as the difference in 100ns ticks between the UNIX and FILETIME epochs.
   *
   */
  public void testSkewBetweenUNIXEpochAndWindowsEpoch() {
    try {
      CDLL dll = CDLL.LoadLibrary("kernel32.dll");

      CFunction systemTimeToFileTime = dll
      .loadFunction("SystemTimeToFileTime");
      
      SYSTEMTIME zeroTime = new SYSTEMTIME();
      zeroTime.wDay.setValue((short)1);
      zeroTime.wMonth.setValue((short)1);
      zeroTime.wYear.setValue((short)1601);
      zeroTime.wHour.setValue((short)0);
      zeroTime.wMinute.setValue((short)0);
      zeroTime.wSecond.setValue((short)0);
      zeroTime.wMilliseconds.setValue((short)0);
      
      SYSTEMTIME UNIXepochTime = new SYSTEMTIME();
      UNIXepochTime.wDay.setValue((short)1);
      UNIXepochTime.wMonth.setValue((short)1);
      UNIXepochTime.wYear.setValue((short)1970);
      UNIXepochTime.wHour.setValue((short)0);
      UNIXepochTime.wMinute.setValue((short)0);
      UNIXepochTime.wSecond.setValue((short)0);
      UNIXepochTime.wMilliseconds.setValue((short)0);
      
      FILETIME zeroFileTime = new FILETIME();
      FILETIME UNIXepochFileTime = new FILETIME();

      Object[] ary1 = { zeroTime, zeroFileTime };
      CInt o = (CInt) systemTimeToFileTime.call(CInt.class, ary1,
          CFunction.FUNCFLAG_STDCALL);
      assertTrue(o.getValue() != 0);

      Object[] ary2 = { UNIXepochTime, UNIXepochFileTime };
      CInt o1 = (CInt) systemTimeToFileTime.call(CInt.class, ary2,
          CFunction.FUNCFLAG_STDCALL);
      assertTrue(o.getValue() != 0);
      
      long zeroCount = (zeroFileTime.dwHighDateTime.getValue() << 32) + (zeroFileTime.dwLowDateTime.getValue());
      long hi = U32Jint2Jlong(UNIXepochFileTime.dwHighDateTime.getValue());
      long lo = U32Jint2Jlong(UNIXepochFileTime.dwLowDateTime.getValue());
      long UNIXepochCount = ( hi << 32) + (lo);
      assertEquals(0, zeroCount);
      assertEquals(0x19db1ded53e8000L, UNIXepochCount - zeroCount);

    } catch (Exception e) {
      fail();
    }
  }
  public static final long U32Jint2Jlong(int i) {
    return (((long)i) & 0xffff0000L) | i & 0x00ffff;
  }
}

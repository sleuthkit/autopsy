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
import isi.pasco2.util.StructConverter;

import java.util.Date;

import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestFileTime extends TestCase {
  
    public static TestSuite suite() {
        return new TestSuite(TestFileTime.class);
    }
    
  public void testFileTime() {
    long low = -543274336;
    long high = 29744990;
    FileTime ft = new FileTime(low, high);
    long millis = ft.getTimeAsMillis();
    //assertEquals(1130902272657L, millis);
    
    Date d = ft.asDate();
    assertEquals("Wed Nov 02 13:31:12 EST 2005", d.toString());
    assertEquals("2005-11-02T03:31:12.657Z", ft.toString());
  }
  
  public void testBitStuff() {
    String hex = "40B3B13F";
    byte[] bytes = new byte[4];
    for (int i=0 ; i < 4 ; i++) {
      bytes[i] = (byte)Integer.parseInt(hex.substring(i*2, i*2+2), 16);
    }
    
    long res = StructConverter.bytesIntoInt(bytes,0);
    assertEquals(1068610368,res);
    long low = Long.parseLong(hex.substring(0,8) , 16);
    low = ((low & 0xFF) << 24) | ((low & 0x0FF00) << 8) | ((low & 0xFF0000) >> 8) | ((low & 0xFF000000)>> 24);
    assertEquals(1068610368,low);
  }
  
  public void testHexLittleEndian() {
    String hex = "40B3B13FED2AC001";
    FileTime ft = FileTime.parseLittleEndianHex(hex);
    //Sat, 30 September 2000 14:46:43  GMT
    assertEquals("2000-09-30T14:46:43.060Z", ft.toString()); 
    
    //Wed, 26 April 2000 22:58:55  GMT
    assertEquals("2000-04-26T22:58:55.899Z", FileTime.parseLittleEndianHex("39B1C8FFD2AFBF01").toString());
    //Sat, 30 March 2002 05:15:11  GMT
    assertEquals("2002-03-30T05:15:11.620Z", FileTime.parseLittleEndianHex("69D8F2DDA9D7C101").toString());
    //Mon, 17 October 2005 02:59:22  UTC
    assertEquals("2005-10-17T02:59:22.147Z", FileTime.parseLittleEndianHex("308F41C6C6D2C501").toString());
  }
  
  public void testHexBigEndian() {
    String hex = "01C1D7A9DDF2D869";
    FileTime ft = FileTime.parseBigEndianHex(hex);
    //Sat, 30 March 2002 05:15:11  GMT
    assertEquals("2002-03-30T05:15:11.620Z", ft.toString()); 
    
    // Sun, 10 March 2002 06:07:32  GMT
    assertEquals("2002-03-10T06:07:32.813Z", FileTime.parseBigEndianHex("01C1C7F9DDFBD86F").toString());
    // Tue, 20 January 2004 04:58:58  GMT
    assertEquals("2004-01-20T04:58:58.985Z", FileTime.parseBigEndianHex("01C3DF121D432432").toString());
    
  }
  
  public void testPassingInts() {
    int[] intsLow = { 0x30, 0x8F, 0x41, 0xC6 };
    byte[] bytesLow = new byte[4];
      
    for (int i=0 ; i < 4 ; i++) {
      bytesLow[i] = (byte) (intsLow[i] & 0xFF); 
    }
    long low = StructConverter.bytesIntoUInt(bytesLow, 0);
    
    long lowA = Long.parseLong("308F41C6", 16);
    lowA = ((lowA & 0xFF) << 24) | ((lowA & 0x0FF00) << 8) | ((lowA & 0xFF0000) >> 8) | ((lowA & 0xFF000000)>> 24);

    assertEquals(lowA, low);
    
    
    int[] intsHigh = { 0xC6, 0xD2, 0xC5, 0x01  };
    byte[] bytesHigh = new byte[4];
      
    for (int i=0 ; i < 4 ; i++) {
      bytesHigh[i] = (byte) (intsHigh[i] & 0xFF); 
    }
    long high = StructConverter.bytesIntoUInt(bytesHigh, 0);

    FileTime ft = new FileTime(low, high);
    assertEquals("2005-10-17T02:59:22.147Z", ft.toString());
    


  }
  
}

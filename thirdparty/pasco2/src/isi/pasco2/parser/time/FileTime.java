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


package isi.pasco2.parser.time;

import isi.pasco2.parser.DateTime;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class FileTime implements DateTime{
  static DateFormat xsdDateFormat = null;
  static DateFormat regularDateFormat = null;
 
  {
    xsdDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    xsdDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    regularDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
    regularDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }
  
  // the remainder in nanoseconds since the unix epoch
  long remainderNanoSeconds;
  
  // the number of seconds since the unix epoch
  long time_t;
  
  public long gettime_t() {
    return time_t;
  }
  
  public long getRemainderNanoSeconds() {
    return remainderNanoSeconds;
  }
  
  public long getTimeAsMillis() {
    return time_t*1000 + (remainderNanoSeconds / 1000000);
  }
 
  public long getMillisRemainder() {
    return remainderNanoSeconds / 1000000;
  }
  
  public long getMicrosRemainder() {
    return remainderNanoSeconds % 1000000;
  }
  
  public Date asDate() {
    return new Date(getTimeAsMillis());
  }
  
  private long low;
  private long high;
   
  public long getHigh() {
    return high;
  }

  public long getLow() {
    return low;
  }

  /**
   *  Create a parser for windows FILETIME timestamps. This is a count of the number 
   *  of 100ns increments since the Epoch Jan 1 1601 at 00:00:00. Credits for the 
   *  algorithm go to  cygin's to_timestruc_t() implementation found in time.cc. 
   *  
   * @param low 32bit unsigned int low value of the timestamp, passed as a java long
   * @param high 32bit unsigned int low value of the timestamp, passed as a java long
   */
  public FileTime(long low, long high) {
    this.low = low;
    this.high = high;
    parse(low, high);
   }
  
  
  // the number of 100 ns ticks between 00:00:00 on Jan 1, 1601 and 00:00:00 Jan 1, 1970
  // validated in the unit test TestPlatform.testSkewBetweenUNIXEpochAndWindowsEpoch
  static long skewFILETIME2timetEpochs = 0x19db1ded53e8000L;
  static long ticksPerSecond = 10 * 1000 * 1000L;
  
  /**
   * 
   *  this code is based on cygin's times.cc/to_timestruc_t(). Author not indicated. 
   *  
   */
  void parse(long low, long high) {
      long rem;
      long x = (high << 32) + (low);

      /* return the UNIX epoch as the FILETIME epoch */
      if (x == 0) {
          time_t = 0;
          rem = 0;
          return;
      }

      x -= skewFILETIME2timetEpochs;        // remove the skew so x now is the number of 
                                        // ticks since 00:00:00 1/1/1970
      rem = x % ticksPerSecond;         // remaining ticks not whole seconds
      x /= ticksPerSecond;         // number of seconds since UNIX epoch   
      remainderNanoSeconds = rem * 100; // as remainder is in nanoseconds 
      time_t = x;
  }

  
   
   public String toString() {
     return xsdDateFormat.format(asDate());
   }
   
   public String toXSDString() {
     return xsdDateFormat.format(asDate());
   }
   
   public String toStandardTime() {
     return xsdDateFormat.format(asDate());
   }
   
   public static FileTime parseLittleEndianHex(String hex) {
       assert hex.length() == 16;
       
       long low = Long.parseLong(hex.substring(0,8) , 16);
       low = ((low & 0xFF) << 24) | ((low & 0x0FF00) << 8) | ((low & 0xFF0000) >> 8) | ((low & 0xFF000000)>> 24);
       long high = Long.parseLong(hex.substring(8,16) , 16);
       high = ((high & 0xFF) << 24) | ((high & 0x0FF00) << 8) | ((high & 0xFF0000) >> 8) | ((high & 0xFF000000)>> 24);

       return new FileTime(low,high);
   }
   
   public static FileTime parseBigEndianHex(String hex) {
     assert hex.length() == 16;
     
     long all = Long.parseLong(hex, 16);
     long low = all & 0x0ffffffffL;
     long high = (all & 0xffffffff00000000L) >> 32;

     return new FileTime(low,high);
 }

  public boolean equals(Object obj) {
    if (obj instanceof FileTime) {
      FileTime f = (FileTime) obj;
      if (f.time_t == this.time_t && f.remainderNanoSeconds == this.remainderNanoSeconds) {
        return true;
      }    
    }
    return false;
  }
   
   
}

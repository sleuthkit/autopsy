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
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
   

/**
 * MS DOS timestamp to java time parser. The MS DOS timestamp is a 32bit timestamp format
 * which stores timestamps in those bits as follows:
 * bit 0 - 4    : second
 * bit 5 - 10   : minute
 * bit 11 - 15  : hour
 * bit 16 - 20  : day (1-31)
 * bit 21 - 24  : month (1-12)
 * bit 25 - 31  : year offset from 1980
 * 
 * Time is based on local time, not UTC
 * @author bradley@greystate.com
 *
 */
public class DOSTime implements DateTime {
  int date;
  int time;
  
  int year;
  int month;
  int day;
  
  int hour;
  int minute;
  int second;
  
  static TimeZone timeZone = null;
  
  static DateFormat xsdDateFormat = null;
  static DateFormat regularDateFormat = null;
  {
    xsdDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    xsdDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    regularDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
    regularDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

  }
  
/**
 * Construct a DOSTime object from two unsigned words which are read sequentially from 
 * a DOS timestamp.
 * 
 * @param date a 16bit undigned word, passed as an int as java does not have this type.
 * @param time a 16bit undigned word, passed as an int as java does not have this type.
 */
  public DOSTime(int date, int time) {
    this.date = date;
    this.time = time;
    parseTime();
    parseDate();
  }
  

  void parseTime() {
      second = (time & 0x1F) * 2;
      minute = (time & 0x7e0) >> 5;
      hour = (time & 0xf800) >> 11;
  }
  
  void parseDate() {
    day = date & 0x1F;
    month = ((date & 0x1e0) >> 5) -1;
    year = ((date & 0x3e00) >> 9) + 1980;   
  }
  
  public static DOSTime parseLittleEndianHex(String hex) {
    assert hex.length() == 8;
    
    int first = Integer.parseInt(hex.substring(0,4) , 16);
    first = ((first & 0xFF) << 8) | ((first & 0x0FF00) >> 8);
    
    int second  = Integer.parseInt(hex.substring(4,8) , 16);
    second = ((second & 0xFF) << 8) | ((second & 0x0FF00) >> 8);
   

    return new DOSTime(second,first);
  }
  
  public Date asDate() {
    Calendar c;
    if (timeZone != null) {
      c = Calendar.getInstance(timeZone);
    } else {
      c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }
    c.set(year, month, day, hour, minute, second);
    c.set(Calendar.MILLISECOND, 0);
    return c.getTime();
  }
  
  public String toString() {
    return xsdDateFormat.format(asDate());
  }
  
  public boolean equals(Object obj) {
    if (obj instanceof DOSTime) {
      DOSTime f = (DOSTime) obj;
      if (f.date == this.date && f.time == this.time) {
        return true;
      }    
    }
    return false;
  }

  /**
   * @return Returns the timeZone.
   */
  public static TimeZone getTimeZone() {
    return timeZone;
  }

  /**
   * @param timeZone The timeZone to set.
   */
  public static void setTimeZone(TimeZone tz) {
    timeZone = tz;
  }
  
  
}

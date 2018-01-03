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


import isi.pasco2.parser.time.DOSTime;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestDOSTime extends TestCase {
  
    public static TestSuite suite() {
        return new TestSuite(TestDOSTime.class);
    }
    
  public void testHexLittleEndian() {
    String hex = "F788F41E";
    DOSTime ft = DOSTime.parseLittleEndianHex(hex);
    //Sat, 30 September 2000 14:46:43  GMT
    assertEquals("1995-07-20T17:07:46.000Z", ft.toString()); 
    
    //Wed, 26 April 2000 22:58:55  GMT
    assertEquals("2000-01-31T15:21:44.000Z", DOSTime.parseLittleEndianHex("B67A3F28").toString());
    //Sat, 30 March 2002 05:15:11  GMT
    assertEquals("2001-10-12T22:10:02.000Z", DOSTime.parseLittleEndianHex("41B14C2B").toString());
  }
  
  public void testCorrectLocal() {
    
    DOSTime ft = new DOSTime(13404, 1075);
    //Sat, 30 September 2000 14:46:43  GMT
    assertEquals("2006-02-28T00:33:38.000Z", ft.toString()); 
  }
}

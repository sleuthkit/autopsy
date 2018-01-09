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


package isi.pasco2.handler;

import isi.pasco2.parser.DateTime;
import isi.pasco2.parser.time.FileTime;
import isi.pasco2.util.HexFormatter;

import java.io.IOException;

public class CountingCacheHandler extends Pasco2CacheHandler {
  int LEAKcount = 0;
  int URLcount = 0;
  int REDRcount = 0;
  int ENTcount = 0;
  int unknowncount = 0;
  
  public void unknownRecord(String type, int offset, byte[] record) {
    try {
      if (type.startsWith("ent")) {
        ENTcount += 1;
      } else {
        unknowncount += 1;
      }
      output.write("Unknown record type at offset: " + Integer.toHexString(offset) + "\r\n");
      output.write(HexFormatter.convertBytesToString(record));
      output.write("\r\n");
    } catch (IOException ex) {
      handleException(ex);
    }   
  }

  public void endDocument() {
    try {
      output.write("LEAK entries: " + Integer.toString(LEAKcount) + "\r\n");
      output.write("REDR entries: " + Integer.toString(REDRcount) + "\r\n");
      output.write("URL entries: " + Integer.toString(URLcount) + "\r\n");
      output.write("ent entries: " + Integer.toString(ENTcount) + "\r\n");
      output.write("unknown entries: " + Integer.toString(unknowncount) + "\r\n");

    } catch (IOException ex) {
      handleException(ex);
    } 
    super.endDocument();
  
  }


  public void LEAKRecord(FileTime accessTime, FileTime modTime, String url, String file, String directory, String httpHeaders) {
    LEAKcount += 1;
    super.LEAKRecord(accessTime, modTime, url, file, directory, httpHeaders);
  }

  public void REDRRecord(String url) {
    REDRcount += 1;
    super.REDRRecord(url);
  }

  public void URLRecord(FileTime accessTime, FileTime modTime, String url, String file, String directory, String httpHeaders) {
    URLcount += 1;
    super.URLRecord(accessTime, modTime, url, file, directory, httpHeaders);
  }
  
  public void URLRecord(DateTime localAccessTime, DateTime accessTime, DateTime modTime, String url, String file, String directory, String httpHeaders) {
    URLcount += 1;
    super.URLRecord( localAccessTime,  accessTime,  modTime,  url,  file,  directory,  httpHeaders);
  }

  
  
}

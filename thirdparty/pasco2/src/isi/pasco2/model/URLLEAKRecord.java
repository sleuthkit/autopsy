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


package isi.pasco2.model;

import isi.pasco2.parser.DateTime;
import isi.pasco2.util.HexFormatter;

public class URLLEAKRecord extends RecognisedRecord {
  public String filename = null;
  public String httpheaders = null;
  public String dirname = null;
  public DateTime modTime = null;
  public DateTime accessTime = null;
  public DateTime localAccessTime = null;
  public int accessedCount = 0;
  
  public URLLEAKRecord(String type, DateTime accessTime, DateTime modTime, String url, String file, String directory, String httpHeaders, int offset, byte[] rec) {
    this.accessTime = accessTime;
    this.modTime = modTime;
    this.url = url;
    this.filename = file;
    this.dirname = directory;
    this.httpheaders = httpHeaders;
    this.offset = offset;
    this.type = type;
    this.buf = rec;
  }
  
  public URLLEAKRecord(String type, DateTime localAccessTime, DateTime accessTime, DateTime modTime, String url, String file, String directory, String httpHeaders, int offset, byte[] rec) {
    this.accessTime = accessTime;
    this.modTime = modTime;
    this.url = url;
    this.filename = file;
    this.dirname = directory;
    this.httpheaders = httpHeaders;
    this.offset = offset;
    this.type = type;
    this.buf = rec;
    this.localAccessTime = localAccessTime;
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(type);
    sb.append(" [");
    sb.append(offset);
    sb.append("]");
    
    if (url != null) {
      sb.append(url);
      sb.append(' ');
    }
    if (filename != null) {
      sb.append(filename);
      sb.append(' ');
    }
    if (dirname != null) {
      sb.append(dirname);
      sb.append(' ');
    }
    if (modTime != null) {
      sb.append(modTime.toString());
      sb.append(' ');
    }
    if (accessTime != null) {
      sb.append(accessTime.toString());
      sb.append(' ');
    }
    if (localAccessTime != null) {
      sb.append(localAccessTime.toString());
      sb.append(' ');
    }
    if (httpheaders != null) {
      sb.append(httpheaders);
      sb.append(' ');
    }

    sb.append("\r\n");
    sb.append(HexFormatter.convertBytesToString(buf));
    
    return sb.toString();
  }
  
  public boolean equals(Object obj) {
    if (!(obj instanceof URLLEAKRecord)) {
      return false;
    }
    
    URLLEAKRecord rec = (URLLEAKRecord) obj;
    if (rec.offset != this.offset) { 
      return false;
    }
    if (!rec.modTime.equals(this.modTime)) {
      return false;
    }   
    if (!rec.accessTime.equals(this.accessTime)) {
      return false;
    }   
    if (!rec.url.equals(this.url)) {
      return false;
    }
    if (rec.filename != null && this.filename == null) {
      return false;
    }
    if (rec.filename == null && this.filename != null) {
      return false;
    }
    if (rec.filename != null && filename != null && !rec.filename.equals(this.filename)) {
      return false;
    }   
    if (rec.dirname != null && dirname != null && !rec.dirname.equals(this.dirname)) {
      return false;
    }   
    if (rec.httpheaders != null && httpheaders != null && !rec.httpheaders.equals(this.httpheaders)) {
      return false;
    }   
    
    if (rec.accessedCount != accessedCount) {
      return false;
    }
    return true;
  }


  public int hashCode() {
    return offset + filename.hashCode() + httpheaders.hashCode() << 1 + dirname.hashCode() << 2 + modTime.hashCode() << 4 + accessTime.hashCode() << 8;
  }
  
  
}

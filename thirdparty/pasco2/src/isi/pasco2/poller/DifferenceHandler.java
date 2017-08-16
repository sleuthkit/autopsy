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


package isi.pasco2.poller;

import isi.pasco2.handler.HistoryAccessHandler;
import isi.pasco2.io.IndexFile;
import isi.pasco2.model.REDRRecord;
import isi.pasco2.model.Record;
import isi.pasco2.model.URLLEAKRecord;
import isi.pasco2.model.UnknownRecord;
import isi.pasco2.parser.DateTime;

import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.TimeZone;
import java.util.Vector;

import org.apache.commons.collections.CollectionUtils;

public class DifferenceHandler implements HistoryAccessHandler {
  SimpleDateFormat xsdDateFormat;
  {
    xsdDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    xsdDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

  }
  
  Vector<Record> currentUrlRecords = new Vector<Record>();
  Vector<Record> newUrlRecords = new Vector<Record>();

  boolean initialized = false;

  String name;
  int offset;
  int length;
  byte[] rec;
  IndexFile indexFile;

  public DifferenceHandler(String name) {
    this.name = name;
  }

  public void URLRecord(DateTime localAccessTime, DateTime accessTime, DateTime modTime, String url,
      String file, String directory, String httpHeaders) {
    URLLEAKRecord u = new URLLEAKRecord("URL", localAccessTime, accessTime, modTime, url, file,
        directory, httpHeaders, offset, rec);
    if (initialized) {
      if (!newUrlRecords.contains(u)) {
        newUrlRecords.add(u);
      }
    } else {
      if (!currentUrlRecords.contains(u)) {
        currentUrlRecords.add(u);
      }
    }
  }
  
  public void URLRecord(DateTime localAccessTime, DateTime accessTime, DateTime modTime, String url, int count) {
    URLLEAKRecord u = new URLLEAKRecord("URL", localAccessTime, accessTime, modTime, url, null, null, null, offset, rec);
    u.accessedCount = count;
    if (initialized) {
      if (!newUrlRecords.contains(u)) {
        newUrlRecords.add(u);
      }
    } else {
      if (!currentUrlRecords.contains(u)) {
        currentUrlRecords.add(u);
      }
    }
  }
  
  public void URLRecord(DateTime localAccessTime, DateTime accessTime, DateTime modTime, String url) {
    URLLEAKRecord u = new URLLEAKRecord("URL", localAccessTime, accessTime, modTime, url, null, null, null, offset, rec);

    if (initialized) {
      if (!newUrlRecords.contains(u)) {
        newUrlRecords.add(u);
      }
    } else {
      if (!currentUrlRecords.contains(u)) {
        currentUrlRecords.add(u);
      }
    }
  }
  public void URLRecord(DateTime accessTime, DateTime modTime, String url,
      String file, String directory, String httpHeaders) {
    URLLEAKRecord u = new URLLEAKRecord("URL", accessTime, modTime, url, file,
        directory, httpHeaders, offset, rec);
    if (initialized) {
      if (!newUrlRecords.contains(u)) {
        newUrlRecords.add(u);
      }
    } else {
      if (!currentUrlRecords.contains(u)) {
        currentUrlRecords.add(u);
      }
    }
  }

  
  public void startDocument(String fileName, float version) {
  }

  public void endDocument() {
    if (!initialized) {
      // it is now
      initialized = true;
    } else {
      Collection<Record> deletedRecords = CollectionUtils.subtract(
          currentUrlRecords, newUrlRecords);
      Collection<Record> newRecords = CollectionUtils.subtract(newUrlRecords,
          currentUrlRecords);

      if (deletedRecords.size() > 0 || newRecords.size() > 0) {
        StringWriter outWriter = new StringWriter();
        outWriter.write(name + "\r\n");
        for (Record rec : newRecords) {
          Calendar c = Calendar.getInstance();
          outWriter.write("New record: (" + xsdDateFormat.format(c.getTime()) + rec.toString() + "\r\n");
        }
        for (Record rec : deletedRecords) {
          outWriter.write("Deleted record: " + rec.toString() + "\r\n");
        }

        System.out.println(outWriter.toString());
      }
      currentUrlRecords = newUrlRecords;
      newUrlRecords = new Vector<Record>();
    }
  }

  public void invalidRecord(int offset) {

  }

  public void LEAKRecord(DateTime accessTime, DateTime modTime, String url,
      String file, String directory, String httpHeaders) {
    URLLEAKRecord u = new URLLEAKRecord("LEAK", accessTime, modTime, url, file,
        directory, httpHeaders, offset, rec);
    if (initialized) {
      if (!newUrlRecords.contains(u)) {
        newUrlRecords.add(u);
      }
    } else {
      if (!currentUrlRecords.contains(u)) {
        currentUrlRecords.add(u);
      }
    }
  }

  public void REDRRecord(String url) {
    REDRRecord u = new REDRRecord(url, offset);
    if (initialized) {
      if (!newUrlRecords.contains(u)) {
        newUrlRecords.add(u);
      }
    } else {
      if (!currentUrlRecords.contains(u)) {
        currentUrlRecords.add(u);
      }
    }
  }

  public void unknownRecord(String type, int offset, byte[] record) {
    UnknownRecord u = new UnknownRecord(record, offset);
    if (initialized) {
      if (!newUrlRecords.contains(u)) {
        newUrlRecords.add(u);
      }
    } else {
      if (!currentUrlRecords.contains(u)) {
        currentUrlRecords.add(u);
      }
    }
  }

  public void unusedRecord(int offset) {
  }

  public void record(int currentOffset, byte[] rec) {
    this.offset = offset;
    this.rec = rec;
    this.length = length;
  }
}

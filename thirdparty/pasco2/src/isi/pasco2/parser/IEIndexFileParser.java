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


package isi.pasco2.parser;

import isi.pasco2.handler.AbstractPrinterHandler;
import isi.pasco2.handler.CacheAccessHandler;
import isi.pasco2.handler.DefaultHandler;
import isi.pasco2.io.IndexFile;
import isi.pasco2.parser.time.FileTime;

import java.io.IOException;

/**
 * A parser of index.dat files produced by MS Internet Explorer. This general class
 * parses the elements common to the cache and the history varieties.
 * 
 * Based on:
 *  * Keith J. Jones's implementation in pasco (http://odessa.sourceforge.net/)
 *  * Louis K. Thomas' documentated reverse engineering efforts (http://www.latenighthacking.com/projects/2003/reIndexDat/)
 *  
 * @author Bradley Schatz
 *
 */
public abstract class IEIndexFileParser {
  public static int BLOCK_SIZE = 0x80;

  float version;

  long filesize;

  public int primaryHashOffset;

  String fileName;

  IndexFile indexFile;

  DefaultHandler handler;
  
  boolean disableAllocationTest = false;

  public IEIndexFileParser(String fileName, IndexFile file,
      DefaultHandler handler) {
    this.fileName = fileName;
    this.indexFile = file;
    this.handler = handler;
  }

  public IEIndexFileParser(String fileName, IndexFile file) {
    this(fileName, file, new AbstractPrinterHandler());
  }

  public void parseFileUsingDeletedMethod(IndexFile file, DefaultHandler handler)
      throws IOException {
    int currrecoff = 0;
    while (currrecoff < filesize) {
      parseRecord(file, currrecoff, handler);
      currrecoff = currrecoff + BLOCK_SIZE;
    }
  }

  /*
   * Hash Record: (+0x00) 'HASH' (+0x04) # blocks size (+0x08) next hash offset
   * (+0x10) first record flags (+0x14) first record offset
   */
  public void parseFile() throws IOException {
    version = readFileFormatVersion(indexFile);
    handler.startDocument(fileName, version);

    long filesize = getFileSize();
    int hashoff = getHashOffset();
    if (hashoff != 0) {
      parseHashBlocks(hashoff);
    }
    handler.endDocument();
  }


  public HashBlockIterator getHashBlocks(int hashoff) throws IOException {
    HashBlockIterator it = new HashBlockIterator(indexFile, this, hashoff);
    return it;
  }
  
  public ValidRecordIterator getValidRecords() throws IOException {
    return new ValidRecordIterator(this);
  }
 
  public RecordIterator getAllRecords() throws IOException {
    return new AllRecordIterator(this);
  }
  
  public void parseHashBlocks(int hashoff) throws IOException {
    RecordIterator it;
    if (disableAllocationTest) {
      it = getAllRecords();
    } else {
      it = getValidRecords();
    }
    
    while (it.hasNext()) {
      parseRecord(indexFile, it.next().recordOffset, handler);
    }

  }

  public long getFileSize() throws IOException {
    indexFile.seek(0x1C);
    filesize = indexFile.readLittleEndianInt();
    return filesize;
  }

  public int getHashOffset() throws IOException {
    int hashoff = indexFile.readIntAtOffset(0x20);
    primaryHashOffset = hashoff;
    return hashoff;
  }

  abstract protected void parseRecord(IndexFile file, int currrecoff,
      DefaultHandler handler) throws IOException; 

  public void parseURLLEAKRecord(String type, IndexFile fr, long currrecoff,
      CacheAccessHandler handler) throws IOException {
    int reclen = fr.readIntAtOffset(currrecoff + 4) * BLOCK_SIZE;
    byte[] rec = new byte[reclen];
    fr.read(rec, 0, reclen);
    
    handler.record((int) currrecoff, rec);

    long low = fr.readUIntAtOffset(currrecoff + 8);
    long high = fr.readLittleEndianUInt();
    FileTime modTime = new FileTime(low, high);

    low = fr.readUIntAtOffset(currrecoff + 16);
    high = fr.readLittleEndianUInt();
    FileTime accessTime = new FileTime(low, high);

    StringBuffer url = readURL(fr, currrecoff);
    StringBuffer filename = readFileName(fr, currrecoff);
    String dirname = readDirName(fr, currrecoff);
    StringBuffer httpheaders = readHTTPHeaders(fr, currrecoff, reclen);

    if (type.startsWith("URL")) {
      handler.URLRecord(accessTime, modTime, url.toString(), makePrintable(
          filename).toString(), dirname, makePrintable(httpheaders).toString());
    } else {
      handler.LEAKRecord(accessTime, modTime, url.toString(), makePrintable(
          filename).toString(), dirname, makePrintable(httpheaders).toString());
    }
  }

  protected StringBuffer readURL(IndexFile fr, long currrecoff)
      throws IOException {
    char c;
    if (version >= 5) {
      fr.seek(currrecoff + 0x34);
    } else {
      fr.seek(currrecoff + 0x38);
    }

    int urloff = fr.readUnsignedByte();
    int i = 0;
    fr.seek(currrecoff + urloff);
    c = fr.readAsciiChar();

    StringBuffer url = new StringBuffer();

    while (c != '\0' && currrecoff + urloff + i + 1 < filesize) {
      url.append(c);
      fr.seek(currrecoff + urloff + i + 1);
      c = fr.readAsciiChar();
      i++;
    }
    return url;
  }

  protected StringBuffer readFileName(IndexFile fr, long currrecoff)
      throws IOException {
    char c;
    int i;
    if (version >= 5) {
      fr.seek(currrecoff + 0x3C);
    } else {
      fr.seek(currrecoff + 0x40);
    }
    long filenameoff = fr.readLittleEndianInt() + currrecoff;

    i = 0;

    StringBuffer filename = new StringBuffer();

    if (filenameoff > currrecoff + 0x3C) {
      fr.seek(filenameoff);
      c = fr.readAsciiChar();
      while (c != '\0' && filenameoff + i + 1 < filesize) {
        filename.append(c);
        fr.seek(filenameoff + i + 1);
        c = fr.readAsciiChar();
        i++;
      }
    }
    return filename;
  }

  protected StringBuffer readHTTPHeaders(IndexFile fr, long currrecoff,
      int reclen) throws IOException {
    char c;
    int i;
    long httpheadersoff;
    if (version >= 5) {
      httpheadersoff = fr.readIntAtOffset(currrecoff + 0x44) + currrecoff;

    } else {
      httpheadersoff = fr.readIntAtOffset(currrecoff + 0x48) + currrecoff;
    }

    i = 0;
    StringBuffer httpheaders = new StringBuffer();
    if (httpheadersoff > currrecoff + 0x44) {
      fr.seek(httpheadersoff);
      c = fr.readAsciiChar();

      while (c != '\0' && httpheadersoff + i + 1 < currrecoff + reclen
          && httpheadersoff + i + 1 < filesize) {
        httpheaders.append(c);
        fr.seek(httpheadersoff + i + 1);
        c = fr.readAsciiChar();
        i++;
      }
    }
    return httpheaders;
  }

  protected String readDirName(IndexFile fr, long currrecoff)
      throws IOException {
    char c;
    if (version >= 5.2) {
      fr.seek(currrecoff + 0x38);
    } else if (version >= 5) {
      fr.seek(currrecoff + 0x39);
    } else {
      fr.seek(currrecoff + 0x3C);
    }
    c = fr.readAsciiChar();
    int dirnameoff = (int) c;

    String dirname;
    if (0x50 + (12 * dirnameoff) + 8 < filesize) {
      dirname = fr.readStringAtOffset(0x50 + (12 * dirnameoff), 8);
    } else {
      dirname = new String();
    }
    return dirname;
  }



  public float readFileFormatVersion(IndexFile reader) throws IOException {
    reader.seek(0x18);
    byte[] versionAry = new byte[3];
    reader.read(versionAry, 0, 3);
    reader.read();
    if (versionAry[0] == 0) {
      return 0F;
    } else {
      return Float.parseFloat(new String(versionAry));
    }
  }

  void parseREDRRecord(IndexFile fr, long currrecoff, DefaultHandler handler)
      throws IOException {

    // int reclen = readIntAtOffset(fr,currrecoff+4 );

    int i = 0;
    fr.seek(currrecoff + 0x10);
    char c = fr.readAsciiChar();

    StringBuffer url = new StringBuffer();

    while (c != '\0' && currrecoff + 0x10 + i + 1 < filesize) {
      url.append(c);
      fr.seek(currrecoff + 0x10 + i + 1);
      c = fr.readAsciiChar();
      i++;
    }

    handler.REDRRecord(url.toString());
  }

  StringBuffer makePrintable(StringBuffer buf) {
    for (int i = 0; i < buf.length(); i++) {
      char c = buf.charAt(i);
      if (((byte) c) < 32 || ((byte) c) > 127) {
        buf.setCharAt(i, ' ');
      }
    }
    return buf;
  }

  public Allocator getAllocator() {
    return new Allocator(this);
  }

  public boolean isDisableAllocationTest() {
    return disableAllocationTest;
  }

  public void setDisableAllocationTest(boolean disableAllocationTest) {
    this.disableAllocationTest = disableAllocationTest;
  }

  
}

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

import isi.pasco2.handler.CacheAccessHandler;
import isi.pasco2.handler.DefaultHandler;
import isi.pasco2.io.IndexFile;
import isi.pasco2.util.StructConverter;

import java.io.IOException;

/**
 * A parser of index.dat files produced by MS Internet Explorer. This specific class
 * parses the history specific elements.
 * 
 *      
 * Based on:
 *  * Keith J. Jones's implementation in pasco (http://odessa.sourceforge.net/)
 *  * Louis K. Thomas' documentated reverse engineering efforts (http://www.latenighthacking.com/projects/2003/reIndexDat/)
 *  
 * @author Bradley Schatz
 *
 */
public class IECacheFileParser extends IEIndexFileParser {

  public IECacheFileParser(String fileName, IndexFile file, DefaultHandler handler) {
    super(fileName, file, handler);
  }

  public IECacheFileParser(String fileName, IndexFile file) {
    super(fileName, file);
  }

  protected void parseRecord(IndexFile file, int currrecoff,
      DefaultHandler handler) throws IOException {
    file.seek(currrecoff);
    byte[] rec;
    CacheAccessHandler cacheHandler = (CacheAccessHandler) handler;
    file.seek(currrecoff);
    byte[] type = new byte[4];
    file.read(type, 0, 4);
    String typeStr = new String(type);
    try {
      if (typeStr.equals("REDR")) {
        parseREDRRecord(file, currrecoff, cacheHandler);
      } else if (typeStr.startsWith("URL") || typeStr.equals("LEAK")) {
        parseURLLEAKRecord(typeStr, file, currrecoff, cacheHandler);
      } else {
        int i = StructConverter.bytesIntoInt(type, 0);
        if (i == 0x0badf00d) {
          // this pattern most likely indicates uninitalized memory as allocated
          // by
          // a memory allocator. This is probably a mmaped file.
          handler.unusedRecord(currrecoff);
        } else {
          rec = new byte[BLOCK_SIZE];
          file.read(rec, 0, BLOCK_SIZE);
          handler.record(currrecoff, rec);
          handler.unknownRecord(typeStr, currrecoff, rec);
        }
      }
    } catch (IOException ex) {
      handler.invalidRecord(currrecoff);
    }
  }
}

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

import gnu.trove.TIntArrayList;

import java.io.IOException;

abstract public class AbstractValidRecordIterator implements RecordIterator {
  HashBlockIterator hashBlockIt = null ;
  HashBlock currentHashBlock = null;
  HashRecordIterator hashRecIt = null;
  IEIndexFileParser parser;
  HashRecord nextRecord = null;
  TIntArrayList seenRecords = new TIntArrayList();

  AbstractValidRecordIterator(IEIndexFileParser parser) throws IOException {
    this.parser = parser;
    int hashOff = parser.getHashOffset();
    if (hashOff != 0) {
      hashBlockIt = parser.getHashBlocks(hashOff);
    }
  }
  
  public HashRecord getNextUnclassifiedRecord() throws IOException {
    if (hashBlockIt == null) {
      return null;
    }
    if (hashRecIt != null) {
      if (hashRecIt.hasNext()) {
        return hashRecIt.next();
      } else {
        if (hashBlockIt.hasNext()) {
          currentHashBlock = hashBlockIt.next();
          hashRecIt = null;
          return getNextUnclassifiedRecord();
        } else { 
          return null;
        }
      }
    } else {
      if (currentHashBlock == null) {
        if (hashBlockIt.hasNext()) {
          currentHashBlock = hashBlockIt.next();
          return getNextUnclassifiedRecord();
        } else {
          return null;
        }
      } else {
        hashRecIt = currentHashBlock.getRecords();
        return getNextUnclassifiedRecord();
      } 
    }
  }
  
  public HashRecord next() {
    HashRecord res = nextRecord;
    nextRecord = null;
    return res;
  }
}

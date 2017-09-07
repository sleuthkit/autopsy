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

import isi.pasco2.io.FastReadIndexFile;
import isi.pasco2.io.IndexFile;
import isi.pasco2.parser.IEHistoryFileParser;
import isi.pasco2.poller.DifferenceHandler;

/**
 * Index.dat difference observer. Periodically polls the index.dat file in question and 
 * notes and changes.
 * 
 * Limitations: Very memory intensive, should watch the unknown sections better.
 * 
 * @author Bradley Schatz
 *
 */
public class Poller {
 public static void main(String[] args) {
   String cachePath = "d:\\Documents and Settings\\bschatz\\Local Settings\\Temporary Internet Files\\Content.IE5\\index.dat";
   String historyPath = "d:\\Documents and Settings\\bschatz\\Local Settings\\History\\History.IE5\\index.dat";
   
   //DifferenceHandler cacheHandler = new DifferenceHandler("Cache");
   DifferenceHandler historyHandler = new DifferenceHandler("History");
   
   //IEIndexFileParser cacheparser = new IEIndexFileParser();
   IEHistoryFileParser historyParser ;
   
   while (true) {
      try {
        //IndexFile f1 = new FastReadIndexFile(cachePath);
        //cacheparser.parseFile(cachePath, f1, cacheHandler);
        //f1.close();
   
        IndexFile f2 = new FastReadIndexFile(historyPath);
        historyParser = new IEHistoryFileParser(cachePath, f2, historyHandler);
        historyParser.parseFile();
        f2.close();
        
        try {
          Thread.sleep(10000);
        } catch (InterruptedException ex) {
        }
      } catch (Exception ex) {
        System.err.println(ex.getMessage());
        ex.printStackTrace();
      }
        
    }

  }
}



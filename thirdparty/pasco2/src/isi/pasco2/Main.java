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

import isi.pasco2.handler.CountingCacheHandler;
import isi.pasco2.handler.Pasco2HistoryHandler;
import isi.pasco2.io.FastReadIndexFile;
import isi.pasco2.io.IndexFile;
import isi.pasco2.parser.IECacheFileParser;
import isi.pasco2.parser.IEHistoryFileParser;
import isi.pasco2.parser.IEIndexFileParser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

/**
 * Main command line parser for pasco2
 * 
 * @author Bradley Schatz
 *
 */
public class Main {
 public static void main(String[] args) {
    CommandLineParser parser = new PosixParser();

    Options options = new Options();
    Option undelete = new Option("d", "Undelete activity records");
    options.addOption(undelete);
    Option disableAllocation = new Option("M", "Disable allocation detection");
    options.addOption(disableAllocation);
    Option fieldDelimeter = OptionBuilder.withArgName( "field-delimeter" )
                      .hasArg()
                      .withDescription( "Field Delimeter (TAB by default)" )
                      .create( "t" );
    options.addOption(fieldDelimeter);
    
    Option timeFormat = OptionBuilder.withArgName( "time-format" )
                    .hasArg()
                    .withDescription( "xsd or standard (pasco1 compatible)" )
                    .create( "f" );
    options.addOption(timeFormat);
    
    Option fileTypeOption = OptionBuilder.withArgName( "file-type" )
                    .hasArg()
                    .withDescription( "The type of file: cache or history" )
                    .create( "T" );
    
    options.addOption(fileTypeOption);

    
    try {
        CommandLine line = parser.parse( options, args );
        boolean undeleteMethod = false;
        String delimeter = null;
        String format = null;
        String fileType = null;
        boolean disableAllocationTest = false;
        
        if( line.hasOption( "d" ) ) {
            undeleteMethod = true; 
        }
        
        if( line.hasOption( 't' ) ) {
          delimeter = line.getOptionValue( 't' ) ;
        }
        
        if( line.hasOption( 'M' ) ) {
          disableAllocationTest = true ;
        }
        
        if( line.hasOption( 'T' ) ) {
          fileType = line.getOptionValue( 'T' ) ;
        }
        
        if( line.hasOption( 'f' ) ) {
          format = line.getOptionValue( 'f' ) ;
        }
        
        if (line.getArgs().length != 1) {
          System.err.println("No file specified.");
          HelpFormatter formatter = new HelpFormatter();
          formatter.printHelp( "pasco2", options );
          System.exit(1);
        }
        String fileName = line.getArgs()[0];
        
        try {
          IndexFile fr = new FastReadIndexFile(fileName, "r");
 
          CountingCacheHandler handler = null;
          
          if (fileType == null) {
            handler = new CountingCacheHandler();
          }
          if (fileType == null) {
            handler = new CountingCacheHandler();
          } else if (fileType.equals("cache")) {
            handler = new CountingCacheHandler();
          } else if (fileType.equals("history")) {
            handler = new Pasco2HistoryHandler();
          } 
          
          if (format != null) {
            if (format.equals("pasco")) { 
              DateFormat regularDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
              handler.setDateFormat(regularDateFormat);
              TimeZone tz = TimeZone.getTimeZone("Australia/Brisbane");
              regularDateFormat.setTimeZone(tz);
              
            } else if (format.equals("standard")) {
              DateFormat xsdDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
              handler.setDateFormat(xsdDateFormat);     
              xsdDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            } else {
              System.err.println("Format not supported.");
              HelpFormatter formatter = new HelpFormatter();
              formatter.printHelp( "pasco2", options );
              System.exit(1);
            }
          }
          
          if (delimeter != null) {
            handler.setDelimeter(delimeter);
          }
          
          IEIndexFileParser logparser = null;
          if (fileType == null) {
            System.err.println("Using cache file parser.");
            logparser = new IECacheFileParser(fileName, fr, handler);
          } else if (fileType.equals("cache")) {
            logparser = new IECacheFileParser(fileName, fr, handler);
          } else if (fileType.equals("history")) {
            logparser = new IEHistoryFileParser(fileName, fr, handler);
          } else {
            System.err.println("Unsupported file type.");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "pasco2", options );
            System.exit(1);
          }
          if (disableAllocationTest) {
            logparser.setDisableAllocationTest(true);
          }
          logparser.parseFile();
          
        } catch (Exception ex) {
          System.err.println(ex.getMessage());
          ex.printStackTrace();
        }
        
    }
    catch( ParseException exp ) {
        System.out.println( "Unexpected exception:" + exp.getMessage() );
    }
  }
}



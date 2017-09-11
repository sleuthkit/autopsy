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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class AbstractPrinterHandler implements DefaultHandler {
  Writer output;
  String delimeter = "\t";
  DateFormat dateFormat = null;

  
  public String getDelimeter() {
    return delimeter;
  }

  public void setDelimeter(String seperator) {
    this.delimeter = seperator;
  }

  public AbstractPrinterHandler(Writer w) {
    output = w;
    dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }
 
  public AbstractPrinterHandler(Writer w, DateFormat df) {
    output = w;
    dateFormat = df;
  }
  
  public AbstractPrinterHandler() {
    output = new PrintWriter(System.out);
    dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }
  
  public void endDocument() {
    try {
      output.close();
    } catch (IOException ex) {
      handleException(ex);
    }
  }


  
  public void LEAKRecord(DateTime accessTime, DateTime modTime, String url, String file, String directory, String httpHeaders) {
    try {
      output.write("LEAK");
      output.write(delimeter);
      output.write(url);
      output.write(delimeter);
      output.write(dateFormat.format(modTime.asDate()));
      output.write(delimeter);
      output.write(dateFormat.format(accessTime.asDate()));
      output.write(delimeter);
      output.write(file);
      output.write(delimeter);
      output.write(directory);
      output.write(delimeter);
      output.write(removeNewlines(httpHeaders));      
      output.write("\r\n");
    } catch (IOException ex) {
      handleException(ex);
    }
  }         

  public void REDRRecord(String url) {
    try {
      output.write("REDR" + delimeter + url + "\r\n");
    } catch (IOException ex) {
      handleException(ex);
    }  
  }


  public void startDocument(String fileName, float version) {
    try {
      output.write("History File: " + fileName + " Version: " + Float.toString(version) + "\r\n\r\n");
      output.write("TYPE" + delimeter + "URL" + delimeter + "MODIFIED TIME" + delimeter + "ACCESS TIME" + delimeter + "FILENAME" + delimeter + "DIRECTORY" + delimeter + "HTTP HEADERS\r\n");
    } catch (IOException ex) {
      handleException(ex);
    }
  }



  public void invalidRecord(int offset) {
    try {
      output.write("Inconsistent record " + Integer.toHexString(offset) + "\r\n");
    } catch (IOException ex) {
      handleException(ex);
    }
  }

  public void unknownRecord(String type, int offset, byte[] record) {
    try {
      output.write("Unknown record type at offset: " + Integer.toHexString(offset) + "\r\n");
    } catch (IOException ex) {
      handleException(ex);
    }   
  }
  protected void handleException(Exception ex) {
    ex.printStackTrace();
  }
  
  String removeNewlines(String str) {
    return str.replaceAll("\r\n", "  ");
  }

  public DateFormat getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(DateFormat dateFormat) {
    this.dateFormat = dateFormat;
  }

  public void unusedRecord(int offset) {
  }

  public void record(int offset, byte[] rec) {
  }
  
  
  
}

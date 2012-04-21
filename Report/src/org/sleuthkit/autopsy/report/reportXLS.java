 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> autopsy <dot> org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.report;

import java.io.FileOutputStream;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * @author Alex
 */
public class reportXLS {
  public static Workbook wb = new XSSFWorkbook();
   public reportXLS(HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> report, reportFilter rr){
    //Empty the workbook first
       Workbook wbtemp = new XSSFWorkbook();
     
      int countGen = 0;
      int countBookmark = 0;
      int countCookie = 0;
      int countHistory = 0;
      int countDownload = 0;
      int countRecentObjects = 0;
      int countTrackPoint = 0;
      int countInstalled = 0;
      int countKeyword = 0;
      int countHash = 0;
      int countDevice = 0;
      for (Entry<BlackboardArtifact,ArrayList<BlackboardAttribute>> entry : report.entrySet()) {
                    if(entry.getKey().getArtifactTypeID() == 1){  
                        countGen++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 2){
                        countBookmark++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 3){

                        countCookie++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 4){

                        countHistory++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 5){
                         countDownload++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 6){
                         countRecentObjects++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 7){
                         countTrackPoint++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 8){
                         countInstalled++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 9){
                         countKeyword++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 10){
                         countHash++;
                    } 
                     if(entry.getKey().getArtifactTypeID() == 11){
                         countDevice++;
                    } 
    }
    
    try{
        Case currentCase = Case.getCurrentCase(); // get the most updated case
         SleuthkitCase skCase = currentCase.getSleuthkitCase();
         String caseName = currentCase.getName();
         Integer imagecount = currentCase.getImageIDs().length;
         Integer filesystemcount = currentCase.getRootObjectsCount();
         Integer totalfiles = skCase.countFsContentType(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG);
         Integer totaldirs = skCase.countFsContentType(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR);
          DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
         DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
         Date date = new Date();
         String datetime = datetimeFormat.format(date);
         String datenotime = dateFormat.format(date);
     
         //The first summary report page
         Sheet sheetSummary = wbtemp.createSheet("Summary"); 
         //Generate a sheet per artifact type
       //  Sheet sheetGen = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getDisplayName()); 
         Sheet sheetHash = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName()); 
         Sheet sheetDevice = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getDisplayName()); 
         Sheet sheetInstalled = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG.getDisplayName()); 
         Sheet sheetKeyword = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName()); 
      //  Sheet sheetTrackpoint = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_TRACKPOINT.getDisplayName()); 
         Sheet sheetRecent = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT.getDisplayName()); 
         Sheet sheetCookie = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getDisplayName()); 
         Sheet sheetBookmark = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getDisplayName()); 
         Sheet sheetDownload = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getDisplayName()); 
         Sheet sheetHistory = wbtemp.createSheet(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getDisplayName());
         
         //Bold/underline cell style for the top header rows
         CellStyle style = wbtemp.createCellStyle();
         style.setBorderBottom((short) 2);
         Font font = wbtemp.createFont();
         font.setFontHeightInPoints((short)16);
         font.setFontName("Courier New");
         font.setBoldweight((short)2);
         style.setFont(font);
         //create the rows in the worksheet for our records
         //Create first row and header
        //  sheetGen.createRow(0);
       //   sheetGen.getRow(0).createCell(0).setCellValue("Name");
       //   sheetGen.getRow(0).createCell(1).setCellValue("Value");
        //  sheetGen.getRow(0).createCell(2).setCellValue("Date/Time");
          
          sheetSummary.createRow(0).setRowStyle(style);
          sheetSummary.getRow(0).createCell(0).setCellValue("Summary Information");
          sheetSummary.getRow(0).createCell(1).setCellValue(caseName);
          //add some basic information
          sheetSummary.createRow(1);
          sheetSummary.getRow(1).createCell(0).setCellValue("# of Images");
          sheetSummary.getRow(1).createCell(1).setCellValue(imagecount);
          sheetSummary.createRow(2);
          sheetSummary.getRow(2).createCell(0).setCellValue("Filesystems found");
          sheetSummary.getRow(2).createCell(1).setCellValue(imagecount);
          sheetSummary.createRow(3);
          sheetSummary.getRow(3).createCell(0).setCellValue("# of Files");
          sheetSummary.getRow(3).createCell(1).setCellValue(totalfiles);
          sheetSummary.createRow(4);
          sheetSummary.getRow(4).createCell(0).setCellValue("# of Directories");
          sheetSummary.getRow(4).createCell(1).setCellValue(totaldirs);
          sheetSummary.createRow(5);
          sheetSummary.getRow(5).createCell(0).setCellValue("Date/Time");
          sheetSummary.getRow(5).createCell(1).setCellValue(datetime);
          
         
         
          sheetHash.createRow(0).setRowStyle(style);
          sheetHash.getRow(0).createCell(0).setCellValue("Name");
          sheetHash.getRow(0).createCell(1).setCellValue("Size");
          sheetHash.getRow(0).createCell(2).setCellValue("Hashset Name");
          
          sheetDevice.createRow(0).setRowStyle(style);
          sheetDevice.getRow(0).createCell(0).setCellValue("Name");
          sheetDevice.getRow(0).createCell(1).setCellValue("Serial #");
          sheetDevice.getRow(0).createCell(2).setCellValue("Time");
          
          sheetInstalled.createRow(0).setRowStyle(style);
          sheetInstalled.getRow(0).createCell(0).setCellValue("Program Name");
          sheetInstalled.getRow(0).createCell(1).setCellValue("Install Date/Time");
          
          sheetKeyword.createRow(0).setRowStyle(style);
          sheetKeyword.getRow(0).createCell(0).setCellValue("Keyword");
          sheetKeyword.getRow(0).createCell(1).setCellValue("File Name");
          sheetKeyword.getRow(0).createCell(2).setCellValue("Preview");
          sheetKeyword.getRow(0).createCell(3).setCellValue("Keyword LIst");
          
          sheetRecent.createRow(0).setRowStyle(style);
          sheetRecent.getRow(0).createCell(0).setCellValue("Name");
          sheetRecent.getRow(0).createCell(1).setCellValue("Path");
          sheetRecent.getRow(0).createCell(2).setCellValue("Related Shortcut");
          
          sheetCookie.createRow(0).setRowStyle(style);
          sheetCookie.getRow(0).createCell(0).setCellValue("URL");
          sheetCookie.getRow(0).createCell(1).setCellValue("Date");
          sheetCookie.getRow(0).createCell(2).setCellValue("Name");
          sheetCookie.getRow(0).createCell(3).setCellValue("Value");
          sheetCookie.getRow(0).createCell(4).setCellValue("Program");
          
          sheetBookmark.createRow(0).setRowStyle(style);
          sheetBookmark.getRow(0).createCell(0).setCellValue("URL");
          sheetBookmark.getRow(0).createCell(1).setCellValue("Title");
          sheetBookmark.getRow(0).createCell(2).setCellValue("Program");
          
          sheetDownload.createRow(0).setRowStyle(style);
          sheetDownload.getRow(0).createCell(0).setCellValue("File");
          sheetDownload.getRow(0).createCell(1).setCellValue("Source");
          sheetDownload.getRow(0).createCell(2).setCellValue("Time");
          sheetDownload.getRow(0).createCell(3).setCellValue("Program");
          
          sheetHistory.createRow(0).setRowStyle(style);
          sheetHistory.getRow(0).createCell(0).setCellValue("URL");
          sheetHistory.getRow(0).createCell(1).setCellValue("Date");
          sheetHistory.getRow(0).createCell(2).setCellValue("Referrer");
          sheetHistory.getRow(0).createCell(3).setCellValue("Title");
          sheetHistory.getRow(0).createCell(4).setCellValue("Program");
          
          for(int i = 0;i < wbtemp.getNumberOfSheets();i++){
              Sheet tempsheet = wbtemp.getSheetAt(i);
              tempsheet.setAutobreaks(true);
              
               for (Row temprow : tempsheet){
                 for (Cell cell : temprow) {
                   cell.setCellStyle(style);
                   tempsheet.autoSizeColumn(cell.getColumnIndex());
                 }
               }
          }
          
          int countedGen = 0;
          int countedBookmark = 0;
          int countedCookie = 0;
          int countedHistory = 0;
          int countedDownload = 0;
          int countedRecentObjects = 0;
          int countedTrackPoint = 0;
          int countedInstalled = 0;
          int countedKeyword = 0;
          int countedHash = 0;
          int countedDevice = 0;
          
         //start populating the sheets in the workbook
         for (Entry<BlackboardArtifact,ArrayList<BlackboardAttribute>> entry : report.entrySet()) {
              if(reportFilter.cancel == true){
                         break;
                        }
              int cc = 0;
               Long objId = entry.getKey().getObjectID();
              FsContent file = skCase.getFsContentById(objId);
              Long filesize = file.getSize();
               TreeMap<Integer, String> attributes = new TreeMap<Integer,String>();
                    // Get all the attributes, line them up to be added. Place empty string placeholders for each attribute type
                 int n;
                 for(n=1;n<=36;n++)
                 {
                     attributes.put(n, "");
                     
                 }
                     for (BlackboardAttribute tempatt : entry.getValue())
                         {
                             if(reportFilter.cancel == true){
                                 break;
                                 }
                         String value = "";
                         int type = tempatt.getAttributeTypeID();
                         if(tempatt.getValueString() == null || "null".equals(tempatt.getValueString())){
                         
                         }
                         else if(type == 2){
                             value  = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date ((tempatt.getValueLong())*1000));
                         }
                         else
                         {
                          value = tempatt.getValueString();
                         }
                         
                          attributes.put(type, value);
                          cc++;
              }
                     

            if(entry.getKey().getArtifactTypeID() == 1){
               countedGen++;
             //  Row temp = sheetGen.getRow(countedGen);
           
            }
            if(entry.getKey().getArtifactTypeID() == 2){
                countedBookmark++;
                Row temp = sheetBookmark.createRow(countedBookmark);
                temp.createCell(0).setCellValue(attributes.get(1));
                temp.createCell(1).setCellValue(attributes.get(3));
                temp.createCell(2).setCellValue(attributes.get(4));
            }
            if(entry.getKey().getArtifactTypeID() == 3){
                 countedCookie++;
                Row temp = sheetCookie.createRow(countedCookie);
                temp.createCell(0).setCellValue(attributes.get(1));
                temp.createCell(1).setCellValue(attributes.get(2));
                temp.createCell(2).setCellValue(attributes.get(3));
                temp.createCell(3).setCellValue(attributes.get(6));
                temp.createCell(4).setCellValue(attributes.get(4));
            }
            if(entry.getKey().getArtifactTypeID() == 4){
                countedHistory++;
                Row temp = sheetHistory.createRow(countedHistory);
                temp.createCell(0).setCellValue(attributes.get(1));
                temp.createCell(1).setCellValue(attributes.get(33));
                temp.createCell(2).setCellValue(attributes.get(32));
                temp.createCell(3).setCellValue(attributes.get(3));
                temp.createCell(4).setCellValue(attributes.get(4));
            }
            if(entry.getKey().getArtifactTypeID() == 5){
                countedDownload++;
                Row temp = sheetDownload.createRow(countedDownload);
                temp.createCell(0).setCellValue(attributes.get(8));
                temp.createCell(1).setCellValue(attributes.get(1));
                temp.createCell(2).setCellValue(attributes.get(33));
                temp.createCell(3).setCellValue(attributes.get(4));
            }
            if(entry.getKey().getArtifactTypeID() == 6){
                countedRecentObjects++;
                Row temp = sheetRecent.createRow(countedRecentObjects);
                temp.createCell(0).setCellValue(attributes.get(3));
                temp.createCell(1).setCellValue(attributes.get(8));
                temp.createCell(2).setCellValue(file.getName());
                temp.createCell(3).setCellValue(attributes.get(4));
            }
            if(entry.getKey().getArtifactTypeID() == 7){
                // sheetTrackpoint.addContent(artifact);
            }
            if(entry.getKey().getArtifactTypeID() == 8){
                countedInstalled++;
                Row temp = sheetInstalled.createRow(countedInstalled);
                temp.createCell(0).setCellValue(attributes.get(4));
                temp.createCell(1).setCellValue(attributes.get(2));
            }
            if(entry.getKey().getArtifactTypeID() == 9){
               countedKeyword++;
                Row temp = sheetKeyword.createRow(countedKeyword);
                temp.createCell(0).setCellValue(attributes.get(10));
                temp.createCell(1).setCellValue(attributes.get(3));
                temp.createCell(2).setCellValue(attributes.get(12));
                temp.createCell(3).setCellValue(attributes.get(13));
            }
            if(entry.getKey().getArtifactTypeID() == 10){
                 countedHash++;
                Row temp = sheetHash.createRow(countedHash);
                temp.createCell(0).setCellValue(file.getName().toString());
                temp.createCell(1).setCellValue(filesize.toString());
                temp.createCell(2).setCellValue(attributes.get(30));
            } 
             if(entry.getKey().getArtifactTypeID() == 11){
                 countedDevice++;
                Row temp = sheetDevice.createRow(countedDevice);
                temp.createCell(0).setCellValue(attributes.get(18));
                temp.createCell(1).setCellValue(attributes.get(20));
                temp.createCell(2).setCellValue(attributes.get(2));
            } 
              
              
              cc++;
           rr.progBarSet(cc);
         }
         
         
         //write out the report to the reports folder
     try {
         FileOutputStream fos = new FileOutputStream(currentCase.getCaseDirectory()+"/Reports/" + caseName + "-" + datenotime + ".xlsx");
         wbtemp.write(fos);
         fos.close();
         wb = wbtemp;
                }
            catch (IOException e) {
              System.err.println(e);
                }

                }   
 
   catch(Exception E)
   {
       String test = E.toString();
   }
        
  }
    
    
}

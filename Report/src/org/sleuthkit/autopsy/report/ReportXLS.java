 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.*;

/**
 *
 * @author Alex
 */
public class ReportXLS implements ReportModule {

    public static Workbook wb = new XSSFWorkbook();
    private static String xlsPath = "";
    private ReportConfiguration config;
    private static ReportXLS instance = null;

    public ReportXLS() {
        //Empty the workbook first
    }

    public static synchronized ReportXLS getDefault() {
        if (instance == null) {
            instance = new ReportXLS();
        }
        return instance;
    }

    @Override
    public String generateReport(ReportConfiguration reportconfig) throws ReportModuleException {
        config = reportconfig;
        ReportGen reportobj = new ReportGen();
        reportobj.populateReport(reportconfig);
        HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>> report = reportobj.Results;
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
        for (Entry<BlackboardArtifact, ArrayList<BlackboardAttribute>> entry : report.entrySet()) {
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getTypeID()) {
                countGen++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()) {
                countBookmark++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()) {

                countCookie++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()) {

                countHistory++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()) {
                countDownload++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID()) {
                countRecentObjects++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TRACKPOINT.getTypeID()) {
                countTrackPoint++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()) {
                countInstalled++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                countKeyword++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                countHash++;
            }
            if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()) {
                countDevice++;
            }
        }

        try {
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
            font.setFontHeightInPoints((short) 14);
            font.setFontName("Arial");
            font.setBoldweight((short) 2);
            style.setFont(font);

            //create 'default' style
            CellStyle defaultstyle = wbtemp.createCellStyle();
            defaultstyle.setBorderBottom((short) 2);
            Font defaultfont = wbtemp.createFont();
            defaultfont.setFontHeightInPoints((short) 14);
            defaultfont.setFontName("Arial");
            defaultfont.setBoldweight((short) 2);
            defaultstyle.setFont(defaultfont);
            //create the rows in the worksheet for our records
            //Create first row and header
            //  sheetGen.createRow(0);
            //   sheetGen.getRow(0).createCell(0).setCellValue("Name");
            //   sheetGen.getRow(0).createCell(1).setCellValue("Value");
            //  sheetGen.getRow(0).createCell(2).setCellValue("Date/Time");
            sheetSummary.setDefaultColumnStyle(1, defaultstyle);
            sheetSummary.createRow(0).setRowStyle(style);
            sheetSummary.getRow(0).createCell(0).setCellValue("Summary Information");
            sheetSummary.getRow(0).createCell(1).setCellValue(caseName);
            //add some basic information
            sheetSummary.createRow(1).setRowStyle(defaultstyle);
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


            sheetHash.setDefaultColumnStyle(1, defaultstyle);
            sheetHash.createRow(0).setRowStyle(style);
            sheetHash.getRow(0).createCell(0).setCellValue("Name");
            sheetHash.getRow(0).createCell(1).setCellValue("Size");
            sheetHash.getRow(0).createCell(2).setCellValue("Hashset Name");

            sheetDevice.setDefaultColumnStyle(1, defaultstyle);
            sheetDevice.createRow(0).setRowStyle(style);
            sheetDevice.getRow(0).createCell(0).setCellValue("Name");
            sheetDevice.getRow(0).createCell(1).setCellValue("Serial #");
            sheetDevice.getRow(0).createCell(2).setCellValue("Time");

            sheetInstalled.setDefaultColumnStyle(1, defaultstyle);
            sheetInstalled.createRow(0).setRowStyle(style);
            sheetInstalled.getRow(0).createCell(0).setCellValue("Program Name");
            sheetInstalled.getRow(0).createCell(1).setCellValue("Install Date/Time");

            sheetKeyword.setDefaultColumnStyle(1, defaultstyle);
            sheetKeyword.createRow(0).setRowStyle(style);
            sheetKeyword.getRow(0).createCell(0).setCellValue("Keyword");
            sheetKeyword.getRow(0).createCell(1).setCellValue("File Name");
            sheetKeyword.getRow(0).createCell(2).setCellValue("Preview");
            sheetKeyword.getRow(0).createCell(3).setCellValue("Keyword LIst");

            sheetRecent.setDefaultColumnStyle(1, defaultstyle);
            sheetRecent.createRow(0).setRowStyle(style);
            sheetRecent.getRow(0).createCell(0).setCellValue("Name");
            sheetRecent.getRow(0).createCell(1).setCellValue("Path");
            sheetRecent.getRow(0).createCell(2).setCellValue("Related Shortcut");

            sheetCookie.setDefaultColumnStyle(1, defaultstyle);
            sheetCookie.createRow(0).setRowStyle(style);
            sheetCookie.getRow(0).createCell(0).setCellValue("URL");
            sheetCookie.getRow(0).createCell(1).setCellValue("Date");
            sheetCookie.getRow(0).createCell(2).setCellValue("Name");
            sheetCookie.getRow(0).createCell(3).setCellValue("Value");
            sheetCookie.getRow(0).createCell(4).setCellValue("Program");

            sheetBookmark.setDefaultColumnStyle(1, defaultstyle);
            sheetBookmark.createRow(0).setRowStyle(style);
            sheetBookmark.getRow(0).createCell(0).setCellValue("URL");
            sheetBookmark.getRow(0).createCell(1).setCellValue("Title");
            sheetBookmark.getRow(0).createCell(2).setCellValue("Program");

            sheetDownload.setDefaultColumnStyle(1, defaultstyle);
            sheetDownload.createRow(0).setRowStyle(style);
            sheetDownload.getRow(0).createCell(0).setCellValue("File");
            sheetDownload.getRow(0).createCell(1).setCellValue("Source");
            sheetDownload.getRow(0).createCell(2).setCellValue("Time");
            sheetDownload.getRow(0).createCell(3).setCellValue("Program");

            sheetHistory.setDefaultColumnStyle(1, defaultstyle);
            sheetHistory.createRow(0).setRowStyle(style);
            sheetHistory.getRow(0).createCell(0).setCellValue("URL");
            sheetHistory.getRow(0).createCell(1).setCellValue("Date");
            sheetHistory.getRow(0).createCell(2).setCellValue("Referrer");
            sheetHistory.getRow(0).createCell(3).setCellValue("Title");
            sheetHistory.getRow(0).createCell(4).setCellValue("Program");

            for (int i = 0; i < wbtemp.getNumberOfSheets(); i++) {
                Sheet tempsheet = wbtemp.getSheetAt(i);
                tempsheet.setAutobreaks(true);

                for (Row temprow : tempsheet) {
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
            for (Entry<BlackboardArtifact, ArrayList<BlackboardAttribute>> entry : report.entrySet()) {
                if (ReportFilter.cancel == true) {
                    break;
                }
                int cc = 0;
                Long objId = entry.getKey().getObjectID();
                FsContent file = skCase.getFsContentById(objId);
                Long filesize = file.getSize();
                TreeMap<Integer, String> attributes = new TreeMap<Integer, String>();
                // Get all the attributes, line them up to be added. Place empty string placeholders for each attribute type
                int n;
                for (n = 1; n <= 36; n++) {
                    attributes.put(n, "");

                }
                for (BlackboardAttribute tempatt : entry.getValue()) {
                    if (ReportFilter.cancel == true) {
                        break;
                    }
                    String value = "";
                    int type = tempatt.getAttributeTypeID();
                    if (tempatt.getValueString() == null || "null".equals(tempatt.getValueString())) {
                    } else if (type == 2 || type == 33) {
                        value = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date((tempatt.getValueLong()) * 1000));
                    } else {
                        value = tempatt.getValueString();
                    }

                    attributes.put(type, value);
                    cc++;
                }


                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO.getTypeID()) {
                    countedGen++;
                    //  Row temp = sheetGen.getRow(countedGen);

                }
                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()) {
                    countedBookmark++;
                    Row temp = sheetBookmark.createRow(countedBookmark);
                    temp.createCell(0).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                    temp.createCell(1).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                    temp.createCell(2).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                }
                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()) {
                    countedCookie++;
                    Row temp = sheetCookie.createRow(countedCookie);
                    temp.createCell(0).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                    temp.createCell(1).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                    temp.createCell(2).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                    temp.createCell(3).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE.getTypeID()));
                    temp.createCell(4).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                }
                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()) {
                    countedHistory++;
                    Row temp = sheetHistory.createRow(countedHistory);
                    temp.createCell(0).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                    temp.createCell(1).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID()));
                    temp.createCell(2).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID()));
                    temp.createCell(3).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                    temp.createCell(4).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                }
                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()) {
                    countedDownload++;
                    Row temp = sheetDownload.createRow(countedDownload);
                    temp.createCell(0).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID()));
                    temp.createCell(1).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                    temp.createCell(2).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID()));
                    temp.createCell(3).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                }
                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID()) {
                    countedRecentObjects++;
                    Row temp = sheetRecent.createRow(countedRecentObjects);
                    temp.createCell(0).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                    temp.createCell(1).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID()));
                    temp.createCell(2).setCellValue(file.getName());
                    temp.createCell(3).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                }
                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TRACKPOINT.getTypeID()) {
                    // sheetTrackpoint.addContent(artifact);
                }
                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()) {
                    countedInstalled++;
                    Row temp = sheetInstalled.createRow(countedInstalled);
                    temp.createCell(0).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                    temp.createCell(1).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                }
                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                    countedKeyword++;
                    Row temp = sheetKeyword.createRow(countedKeyword);
                    temp.createCell(0).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()));
                    temp.createCell(1).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                    temp.createCell(2).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID()));
                    temp.createCell(3).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()));
                }
                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                    countedHash++;
                    Row temp = sheetHash.createRow(countedHash);
                    temp.createCell(0).setCellValue(file.getName().toString());
                    temp.createCell(1).setCellValue(filesize.toString());
                    temp.createCell(2).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()));
                }
                if (entry.getKey().getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()) {
                    countedDevice++;
                    Row temp = sheetDevice.createRow(countedDevice);
                    temp.createCell(0).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID()));
                    temp.createCell(1).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID()));
                    temp.createCell(2).setCellValue(attributes.get(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                }
            }


            //write out the report to the reports folder, set the wbtemp to the primary wb object
            wb = wbtemp;
            xlsPath = currentCase.getCaseDirectory() + File.separator + "Reports" + File.separator + caseName + "-" + datenotime + ".xlsx";
            this.save(xlsPath);

        } catch (Exception E) {
            String test = E.toString();
        }

        return xlsPath;
    }

    @Override
    public void save(String path) {
        try {
            FileOutputStream fos = new FileOutputStream(path);
            wb.write(fos);
            fos.close();
        } catch (IOException e) {
            Logger.getLogger(ReportXLS.class.getName()).log(Level.SEVERE, "Could not write out XLS report!", e);
        }

    }

    @Override
    public String getName() {
        String name = "Excel";
        return name;
    }

    @Override
    public String getReportType() {
        String type = "XLS";
        return type;
    }
       @Override
    public String getExtension() {
        String ext = ".xlsx";
        return ext;
    }

    @Override
    public ReportConfiguration GetReportConfiguration() {
        return config;
    }

    @Override
    public String getReportTypeDescription() {
        String desc = "This is an xls formatted report that is meant to be viewed in Excel.";
        return desc;
    }

    @Override
    public void getPreview(String path) {
        File file = new File(path);
        try {
            Desktop.getDesktop().open(file);
        } catch (IOException e) {
            Logger.getLogger(ReportXLS.class.getName()).log(Level.SEVERE, "Could not open XLS report! ", e);
        }
    }
}

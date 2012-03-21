/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.report;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * @author Alex
 */
public class reportHTML {
    
    //Declare our publically accessible formatted report, this will change everytime they run a report
    public static StringBuilder formatted_Report = new StringBuilder();
    public static String htmlPath = "";
public reportHTML (HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> report, reportFilter rr){
    
    //This is literally a terrible way to count up all the types of artifacts, and doesn't include any added ones. 
    //Unlike the XML report, which is dynamic, this is formatted and needs to be redone later instead of being hardcoded.
   
      int countGen = 0;
      int countWebBookmark = 0;
      int countWebCookie = 0;
      int countWebHistory = 0;
      int countWebDownload = 0;
      int countRecentObjects = 0;
      int countTrackPoint = 0;
      int countInstalled = 0;
      int countKeyword = 0;
      int countHash = 0;
      for (Entry<BlackboardArtifact,ArrayList<BlackboardAttribute>> entry : report.entrySet()) {
                    if(entry.getKey().getArtifactTypeID() == 1){  
                        countGen++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 2){
                        countWebBookmark++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 3){

                        countWebCookie++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 4){

                        countWebHistory++;
                    }
                    if(entry.getKey().getArtifactTypeID() == 5){
                         countWebDownload++;
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
    }
            
        try{
            
             String ingestwarning = "<h2 style=\"color: red;\">Warning, this report was run before ingest services completed!</h2>";
             Case currentCase = Case.getCurrentCase(); // get the most updated case
             SleuthkitCase skCase = currentCase.getSleuthkitCase();
             String caseName = currentCase.getName();
             Integer imagecount = currentCase.getImageIDs().length;
             Integer totalfiles = 0; //skCase.countFsContentType(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG);
             Integer totaldirs = 0; //skCase.countFsContentType(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR);
             int reportsize = report.size();
             Integer filesystemcount = currentCase.getRootObjectsCount();
             DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
             DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy");
             Date date = new Date();
             String datetime = datetimeFormat.format(date);
             String datenotime = dateFormat.format(date);
             String CSS = "<style>"
                     + "body {padding: 30px; margin: 0; background: #FFFFFF; font: 13px/20px Arial, Helvetica, sans-serif; color: #535353;} "
                   + "h1 {font-size: 26px; color: #005577; margin: 0 0 20px 0;} "
                    + "h2 {font-size: 20px; font-weight: normal; color: #0077aa; margin: 40px 0 10px 0; padding: 0 0 10px 0; border-bottom: 1px solid #dddddd;} "
                     + "h3 {font-size: 16px;color: #0077aa; margin: 40px 0 10px 0;} "
                     + "p {margin: 0 0 20px 0;} table {width: 100%; padding: 0; margin: 0; border-collapse: collapse; border-bottom: 1px solid #e5e5e5;} "
                     + "table thead th {display: table-cell; text-align: left; padding: 8px 16px; background: #e5e5e5; color: #777;font-size: 11px;text-shadow: #e9f9fd 0 1px 0; border-top: 1px solid #dedede; border-bottom: 2px solid #dedede;} "
                     + "table tr th:nth-child(1) {text-align: center; width: 60px;} "
                   + "table td {display: table-cell; padding: 8px 16px; font: 13px/20px Arial, Helvetica, sans-serif;} "
                     + "table tr:nth-child(even) td {background: #f3f3f3;} "
                   + "table tr td:nth-child(1) {text-align: center; width: 60px; background: #f3f3f3;} "
                     + "table tr:nth-child(even) td:nth-child(1) {background: #eaeaea;}"
                     + "</style>";
             //Add additional header information
            formatted_Report.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"en\"><head><title>Autopsy Report for Case:").append(caseName).append("</title>");
            formatted_Report.append(CSS);
            //formatted_Report.append("<link rel=\"stylesheet\" href=\"" + rrpath + "report.css\" type=\"text/css\" />");
            formatted_Report.append("</head><body><div id=\"main\"><div id=\"content\">");
            // Add summary information now
           
            formatted_Report.append("<h1>Report for Case: ").append(caseName).append("</h1>");
            if(IngestManager.getDefault().isIngestRunning())
            {
                formatted_Report.append(ingestwarning);
            }
            formatted_Report.append("<h2>Case Summary</h2><p>HTML Report Generated by <strong>Autopsy 3</strong> on ").append(datetime).append("<br /><ul>");
            formatted_Report.append("<li># of Images: ").append(imagecount).append("</li>");
            formatted_Report.append("<li>FileSystems: ").append(filesystemcount).append("</li>");
            formatted_Report.append("<li># of Files: ").append(totalfiles.toString()).append("</li>");
            formatted_Report.append("<li># of Dirs: ").append(totaldirs.toString()).append("</li>");
            formatted_Report.append("<li># of Artifacts: ").append(reportsize).append("</li>");
            String tableHeader = "<table><thead><tr><th>Artifact ID</th><th>Name</th><th>Size</th>";
             StringBuilder nodeGen = new StringBuilder("<h3>General Information (").append(countGen).append(")</h3>").append(tableHeader).append("<th>Attribute</th><th>Value</th></tr></thead><tbody>");
             StringBuilder nodeWebBookmark =  new StringBuilder("<h3>Web Bookmarks (").append(countWebBookmark).append(")</h3>").append(tableHeader).append("<th>URL</th><th>Title</th><th>Program</th></tr></thead><tbody>");
             StringBuilder nodeWebCookie =  new StringBuilder("<h3>Web Cookies (").append(countWebCookie).append(")</h3>").append(tableHeader).append("<th>URL</th><th>Date</th><th>Name</th><th>Value</th><th>Program</th></tr></thead><tbody>");
             StringBuilder nodeWebHistory =  new StringBuilder("<h3>Web History (").append(countWebHistory).append(")</h3>").append(tableHeader).append("<th>URL</th><th>Date</th><th>Referrer</th><th>Title</th><th>Program</th></tr></thead><tbody>");
             StringBuilder nodeWebDownload =  new StringBuilder("<h3>Web Downloads (").append(countWebDownload).append(")</h3>").append(tableHeader).append("<th>Attribute</th><th>Value</th></tr></thead><tbody>");
             StringBuilder nodeRecentObjects =  new StringBuilder("<h3>Recent Documents (").append(countRecentObjects).append(")</h3>").append(tableHeader).append("<th>Name</th><th>Path</th></tr></thead><tbody>");
             StringBuilder nodeTrackPoint =  new StringBuilder("<h3>Track Points (").append(countTrackPoint).append(")</h3>").append(tableHeader).append("<th>Attribute</th><th>Value</th></tr></thead><tbody>");
             StringBuilder nodeInstalled =  new StringBuilder("<h3>Installed Programs (").append(countInstalled).append(")</h3>").append(tableHeader).append("<th>Attribute</th><th>Value</th></tr></thead><tbody>");
             StringBuilder nodeKeyword =  new StringBuilder("<h3>Keyword Search Hits (").append(countKeyword).append(")</h3>");
             StringBuilder nodeHash =  new StringBuilder("<h3>Hashset Hits (").append(countHash).append(")</h3>").append(tableHeader).append("<th>File Name</th><th>Hashset Name</th></tr></thead><tbody>");
            
             for (Entry<BlackboardArtifact,ArrayList<BlackboardAttribute>> entry : report.entrySet()) {
                 if(reportFilter.cancel == true){
                     break;
                 }
                 int cc = 0;
               StringBuilder artifact = new StringBuilder("");
                Long objId = entry.getKey().getObjectID();
                //Content file = skCase.getContentById(objId);
                FsContent file = skCase.getFsContentById(objId);
           
                Long filesize = file.getSize();
                 artifact.append("<tr><td>").append(objId.toString());
                 artifact.append("</td><td><strong>").append(file.getName().toString()).append("</strong></td>");
                 artifact.append("<td>").append(filesize.toString()).append("</td>");  
                 
                 TreeMap<Integer, String> attributes = new TreeMap<Integer,String>();
                    // Get all the attributes, line them up to be added.
                     for (BlackboardAttribute tempatt : entry.getValue())
                         {
                             if(reportFilter.cancel == true){
                                 break;
                                 }
                          
                         int type = tempatt.getAttributeTypeID();
                         String value = tempatt.getValueString();
                          attributes.put(type, value);
                          cc++;
                         }
                     
                    
                    if(entry.getKey().getArtifactTypeID() == 1){  
                        
                        artifact.append("</tr>");
                        nodeGen.append(artifact);
                    }
                    if(entry.getKey().getArtifactTypeID() == 2){
                        artifact.append("<td>").append(attributes.get(1)).append("</td>");
                        artifact.append("<td>").append(attributes.get(3)).append("</td>");
                        artifact.append("<td>").append(attributes.get(4)).append("</td>");
                        artifact.append("</tr>");
                        nodeWebBookmark.append(artifact);
                    }
                    if(entry.getKey().getArtifactTypeID() == 3){
                        artifact.append("<td>").append(attributes.get(1)).append("</td>");
                        artifact.append("<td>").append(attributes.get(2)).append("</td>");
                        artifact.append("<td>").append(attributes.get(3)).append("</td>");
                        artifact.append("<td>").append(attributes.get(6)).append("</td>");
                        artifact.append("<td>").append(attributes.get(4)).append("</td>");
                        artifact.append("</tr>");
                        nodeWebCookie.append(artifact);
                    }
                    if(entry.getKey().getArtifactTypeID() == 4){
                        artifact.append("<td>").append(attributes.get(1)).append("</td>");
                        artifact.append("<td>").append(attributes.get(2)).append("</td>");
                        artifact.append("<td>").append(attributes.get(32)).append("</td>");
                        artifact.append("<td>").append(attributes.get(3)).append("</td>");
                        artifact.append("<td>").append(attributes.get(4)).append("</td>");
                        artifact.append("</tr>");
                        nodeWebHistory.append(artifact);
                    }
                    if(entry.getKey().getArtifactTypeID() == 5){
                        
                         artifact.append("</tr>");
                         nodeWebDownload.append(artifact);
                    }
                    if(entry.getKey().getArtifactTypeID() == 6){
                        
                         artifact.append("</tr>");
                         nodeRecentObjects.append(artifact);
                    }
                    if(entry.getKey().getArtifactTypeID() == 7){
                        
                         artifact.append("</tr>");
                         nodeTrackPoint.append(artifact);
                    }
                    if(entry.getKey().getArtifactTypeID() == 8){
                        
                         artifact.append("</tr>");
                         nodeInstalled.append(artifact);
                    }
                    if(entry.getKey().getArtifactTypeID() == 9){
                         
                       //  artifact.append("<table><thead><tr><th>Artifact ID</th><th>Name</th><th>Size</th>");
                         
                     //    artifact.append("</tr></table>");
                     //    nodeKeyword.append(artifact);
                    }
                    if(entry.getKey().getArtifactTypeID() == 10){
                        
                         artifact.append("<td>").append(attributes.get(31)).append("</td>");
                         artifact.append("<td>").append(attributes.get(30)).append("</td>");
                         artifact.append("</tr>");
                         nodeHash.append(artifact);
                    } 
                    cc++;
                     rr.progBarSet(cc);
             }
            //Add them back in order
            formatted_Report.append(nodeGen);
            formatted_Report.append("</tbody></table>");
            formatted_Report.append(nodeWebBookmark);
            formatted_Report.append("</tbody></table>");
            formatted_Report.append(nodeWebCookie);
            formatted_Report.append("</tbody></table>");
            formatted_Report.append(nodeWebHistory);
            formatted_Report.append("</tbody></table>");
            formatted_Report.append(nodeWebDownload);
            formatted_Report.append("</tbody></table>");
            formatted_Report.append(nodeRecentObjects);
            formatted_Report.append("</tbody></table>");
            formatted_Report.append(nodeTrackPoint);
            formatted_Report.append("</tbody></table>");
            formatted_Report.append(nodeInstalled);
            formatted_Report.append("</tbody></table>");           
            formatted_Report.append(nodeKeyword);
            if(countKeyword > 0){
            report keywords = new report();
            formatted_Report.append(keywords.getGroupedKeywordHit());
            // "<table><thead><tr><th>Artifact ID</th><th>Name</th><th>Size</th>
           // formatted_Report.append("</tbody></table>");
            }
            
            formatted_Report.append(nodeHash); 
            formatted_Report.append("</tbody></table>");
            //end of master loop
            
                formatted_Report.append("</div></div></body></html>");
                
                  htmlPath = currentCase.getCaseDirectory()+"/Reports/" + caseName + "-" + datenotime + ".html";
                   BufferedWriter out = new BufferedWriter(new FileWriter(htmlPath));
                   out.write(formatted_Report.toString());
                   
                  out.flush();
                  out.close();
           
        }
            catch(Exception e)
            {

                Logger.getLogger(reportHTML.class.getName()).log(Level.INFO, "Exception occurred", e);
            }
        }

    
}
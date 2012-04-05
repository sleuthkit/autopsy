/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.report;

import java.io.FileOutputStream;

import java.util.ArrayList;
import java.util.HashMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 *
 * @author Alex
 */
public class reportXLS {
    
   public reportXLS(HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> report, reportFilter rr){
    Workbook wb = new XSSFWorkbook();
    
    try{
 FileOutputStream fos = new FileOutputStream("sample.xlsx");

 Sheet sh = wb.createSheet("new sheet 1");

 for (int k = 0; k < 30; k++) {

 Row row = sh.createRow((short)k);

 for (int i = 0; i < 30; i++) {

 Cell cell = row.createCell((short)i);

 cell.setCellValue("hi world : )");
 }
 }
 wb.write(fos);
 fos.close();
    }
   catch(Exception E)
   {
       
   }
        
    }
    
    
}

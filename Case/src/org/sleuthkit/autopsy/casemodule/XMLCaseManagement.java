/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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

package org.sleuthkit.autopsy.casemodule;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;

/**
 *
 * This class is used to create, access, and modify the XML configuration file,
 * where we store the information about the case and image(s). This class uses
 * the document handler to store the information about the case. The document
 * handler will be created / opened when the case configuration file is created/
 * opened.
 *
 * @author jantonius
 */
public class XMLCaseManagement implements CaseConfigFileInterface{
    final static String TOP_ROOT_NAME = "AutopsyCase";
    final static String CASE_ROOT_NAME = "Case";

    // general metadata about the case file
    final static String NAME = "Name";
    final static String NUMBER = "Number";
    final static String EXAMINER = "Examiner";
    final static String CREATED_DATE_NAME = "CreatedDate";
    final static String MODIFIED_DATE_NAME = "ModifiedDate";
    final static String SCHEMA_VERSION_NAME = "SchemaVersion";
    final static String AUTOPSY_CRVERSION_NAME = "AutopsyCreatedVersion";
    final static String AUTOPSY_MVERSION_NAME = "AutopsySavedVersion";

    // disk image paths
    final static String IMAGES_NAME = "Images";
    final static String IMG_SET_NAME = "ImgSet";
    final static String IMG_PATH_NAME = "ImgPath";
    final static String IMG_DB_NAME = "ImgDb";
    final static String NEXT_ID_NAME = "NextID";

    // image attribute
    final static String IMG_SET_COUNT = "count";
    final static String IMG_SET_ID = "id";
    final static String IMG_PATH_ID = "idx";
    final static String TIMEZONE = "timezone";

    // folders inside case directory
    final static String LOG_FOLDER_NAME = "LogFolder";
    final static String LOG_FOLDER_RELPATH = "Log";
    final static String TEMP_FOLDER_NAME = "TempFolder";
    final static String TEMP_FOLDER_RELPATH = "Temp";
    final static String EXPORT_FOLDER_NAME = "ExportFolder";
    final static String EXPORT_FOLDER_RELPATH = "Export";

    // folders attribute
    final static String RELATIVE_NAME = "Relative";	// relevant path info

    // folder attr values
    final static String RELATIVE_TRUE = "true";     // if it's a relative path
    final static String RELATIVE_FALSE = "false";   // if it's not a relative path

    // the document
    private Document doc;

    // general info
    private DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss (z)");
    private String caseDirPath;     // case directory path
    private String caseName;        // case name
    private String caseNumber;         // case number
    private String examiner;        // examiner name
    private String schemaVersion = "1.0";
    private String autopsySavedVersion;
    
    // for error handling
    private JPanel caller;
    private String className = this.getClass().toString();

    /** The constructor */
    XMLCaseManagement() throws Exception {
        String autopsyVer = Case.getAutopsyVersion();
//        System.setProperty("netbeans.buildnumber", autopsyVer); // set the current autopsy version // moved to CoreComponents installer
        autopsySavedVersion = System.getProperty("netbeans.buildnumber");
    }

    /**
     * Sets the case directory path of the case directory on the local variable
     *
     * @param givenPath  the new path to be stored as case directory path
     */
    private void setCaseDirPath(String givenPath){
        caseDirPath = givenPath; // change this to change the xml file if needed
    }

    /**
     * Sets the case Name on the XML configuration file
     *
     * @param givenCaseName  the new case name to be set
     */
    @Override
    public void setCaseName(String givenCaseName) throws Exception {
        // change this to change the xml file if needed
        Element nameElement = (Element) getCaseElement().getElementsByTagName(NAME).item(0);
        nameElement.setTextContent(givenCaseName);
        doc.normalize();

        // edit the modified data
        String newDate = dateFormat.format(new Date());
        Element rootEl = getRootElement();
        rootEl.getElementsByTagName(MODIFIED_DATE_NAME).item(0).setTextContent(newDate);

        try {
            writeFile();
        } catch (Exception ex) {
            throw new Exception("Cannot update the case name in the XML config file.", ex);
        }
    }
    
    /**
     * Sets the case number on the XML configuration file
     *
     * @param givenCaseNumber  the new case number to be set
     */
    @Override
    public void setCaseNumber(String givenCaseNumber) throws Exception {
        // change this to change the xml file if needed
        Element nameElement = (Element) getCaseElement().getElementsByTagName(NUMBER).item(0);
        nameElement.setTextContent(String.valueOf(givenCaseNumber));
        doc.normalize();

        // edit the modified data
        String newDate = dateFormat.format(new Date());
        Element rootEl = getRootElement();
        rootEl.getElementsByTagName(MODIFIED_DATE_NAME).item(0).setTextContent(newDate);

        try {
            writeFile();
        } catch (Exception ex) {
            throw new Exception("Cannot update the case name in the XML config file.", ex);
        }
    }
    
    /**
     * Sets the examiner on the XML configuration file
     *
     * @param givenExaminer  the new examiner to be set
     */
    @Override
    public void setCaseExaminer(String givenExaminer) throws Exception {
        // change this to change the xml file if needed
        Element nameElement = (Element) getCaseElement().getElementsByTagName(EXAMINER).item(0);
        nameElement.setTextContent(givenExaminer);
        doc.normalize();

        // edit the modified data
        String newDate = dateFormat.format(new Date());
        Element rootEl = getRootElement();
        rootEl.getElementsByTagName(MODIFIED_DATE_NAME).item(0).setTextContent(newDate);

        try {
            writeFile();
        } catch (Exception ex) {
            throw new Exception("Cannot update the case name in the XML config file.", ex);
        }
    }
    
    /**
     * Sets the case name internally (on local variable in this class)
     * 
     * @param givenCaseName  the new case name
     */
    private void setName(String givenCaseName){
        caseName = givenCaseName; // change this to change the xml file if needed
    }
    
    /**
     * Sets the case number internally (on local variable in this class)
     * 
     * @param givenCaseNumber  the new case number
     */
    private void setNumber(String givenCaseNumber){
        caseNumber = givenCaseNumber; // change this to change the xml file if needed
    }
    
    /**
     * Sets the examiner name internally (on local variable in this class)
     * 
     * @param givenExaminer  the new examiner
     */
    private void setExaminer(String givenExaminer){
        examiner = givenExaminer; // change this to change the xml file if needed
    }
    
    /**
     * Gets the case Name from the document handler
     *
     * @return caseName  the case name from the document handler
     */
    @Override
    public String getCaseName(){
        if(doc == null){
            return "";
        }
        else{
            Element nameElement = (Element) getCaseElement().getElementsByTagName(NAME).item(0);
            String result = nameElement.getTextContent();
            return result;
        }
    }
    
    /**
     * Gets the case Number from the document handler
     *
     * @return caseNumber  the case number from the document handler
     */
    @Override
    public String getCaseNumber(){
        if(doc == null){
            return "";
        }
        else{
            Element numberElement = (Element) getCaseElement().getElementsByTagName(NUMBER).item(0);
            String result = "-1";
            if(numberElement != null)
                result = numberElement.getTextContent();
            return result;
        }
    }
    
    /**
     * Gets the examiner from the document handler
     *
     * @return examiner  the examiner from the document handler
     */
    @Override
    public String getCaseExaminer(){
        if(doc == null){
            return "";
        }
        else{
            Element examinerElement = (Element) getCaseElement().getElementsByTagName(EXAMINER).item(0);
            String result = "";
            if(examinerElement != null)
                result = examinerElement.getTextContent();
            return result;
        }
    }

    /**
     * Gets the case directory path that's stored in this class
     *
     * @return caseDirPath  the case directory path
     */
    public String getCaseDirectory(){
        if(doc == null){
            return "";
        }
        else{
            return caseDirPath;
        }
        // Note: change this to get the case name from the xml file if needed
    }

    /**
     * Gets the Root Element from the document handler
     *
     * @return rootElement  the root element on the document handler
     */
    private Element getRootElement(){
        if(doc != null){
            return doc.getDocumentElement();
        }
        else{
            return null; // should throw error or exception
        }
    }

    /**
     * Gets the created Date from the document handler
     *
     * @return createdDate  the creation date of this case
     */
    protected String getCreatedDate(){
        if(doc != null){
            Element crDateElement = (Element) getRootElement().getElementsByTagName(CREATED_DATE_NAME).item(0);
            return crDateElement.getTextContent();
        }
        else{
            return ""; // should throw error or exception
        }
    }

    /**
     * Gets the Modified Date from the document handler
     *
     * @return modifiedDate  the modification date of this case
     */
    protected String getModifiedDate(){
        if(doc != null){
            Element mDateElement = (Element) getRootElement().getElementsByTagName(MODIFIED_DATE_NAME).item(0);
            return mDateElement.getTextContent();
        }
        else{
            return ""; // should throw error or exception
        }
    }

    /**
     * Gets the Autopsy Created Version from the document handler
     *
     * @return createdVersion  the version of autopsy when this case was created
     */
    protected String getCreatedVersion(){
        if(doc != null){
            Element crVerElement = (Element) getRootElement().getElementsByTagName(AUTOPSY_CRVERSION_NAME).item(0);
            return crVerElement.getTextContent();
        }
        else{
            return ""; // should throw error or exception
        }
    }

    /**
     * Gets the Autopsy Saved Version from the document handler
     *
     * @return savedVersion  the latest version of autopsy when this case is saved
     */
    protected String getSavedVersion(){
        if(doc != null){
            Element mVerElement = (Element) getRootElement().getElementsByTagName(AUTOPSY_MVERSION_NAME).item(0);
            return mVerElement.getTextContent();
        }
        else{
            return ""; // should throw error or exception
        }
    }

    /**
     * Gets the Schema Version from the document handler
     *
     * @return schemaVersion  the schema version of this XML configuration file
     */
    protected String getSchemaVersion(){
        if(doc != null){
            Element schemaVerElement = (Element) getRootElement().getElementsByTagName(SCHEMA_VERSION_NAME).item(0);
            return schemaVerElement.getTextContent();
        }
        else{
            return ""; // should throw error or exception
        }
    }

    /**
     * Gets the Case Element from the document handler
     *
     * @return caseElement  the "Case" element
     */
    private Element getCaseElement(){
        if(doc != null){
            return (Element) doc.getElementsByTagName(CASE_ROOT_NAME).item(0);
        }
        else{
            return null; // should throw error or exception
        }
    }

    /**
     * Gets the full path to the log directory
     *
     * @return logDir  the full path of the "Log" directory
     */
    protected String getLogDir(){
        if(doc != null){
            Element logElement = (Element)getCaseElement().getElementsByTagName(LOG_FOLDER_NAME).item(0);
            if(logElement.getAttribute(RELATIVE_NAME).equals(RELATIVE_TRUE)){
                return caseDirPath + File.separator + logElement.getTextContent();
            }
            else{
                return logElement.getTextContent();
            }
        }
        else{
            return ""; // should throw error or exception
        }
    }

    /**
     * Gets the full path to the temp directory
     *
     * @return tempDir  the full path of the "Temp" directory
     */
    protected String getTempDir(){
        if(doc != null){
            Element tempElement = (Element)getCaseElement().getElementsByTagName(TEMP_FOLDER_NAME).item(0);
            if(tempElement.getAttribute(RELATIVE_NAME).equals(RELATIVE_TRUE)){
                return caseDirPath + File.separator + tempElement.getTextContent();
            }
            else{
                return tempElement.getTextContent();
            }
        }
        else{
            return ""; // should throw error or exception
        }
    }

    /**
     * Gets the full path to the Export directory
     *
     * @return exportDir  the full path of the "Export" directory
     */
    protected String getExportDir(){
        if(doc != null){
            Element exportElement = (Element)getCaseElement().getElementsByTagName(EXPORT_FOLDER_NAME).item(0);
            if(exportElement.getAttribute(RELATIVE_NAME).equals(RELATIVE_TRUE)){
                return caseDirPath + File.separator + exportElement.getTextContent();
            }
            else{
                return exportElement.getTextContent();
            }
        }
        else{
            return ""; // should throw error or exception
        }
    }

    /**
     * Gets image Element from the document handler
     *
     * @return imageElement  the "Image" element
     */
    private Element getImagesElement(){
        if(doc != null){
            return (Element) doc.getElementsByTagName(IMAGES_NAME).item(0);
        }
        else{
            return null; // should throw error or exception
        }
    }
//
//    /**
//     * Looks up for the Image Set Element in the document handler with the given
//     * image ID.
//     *
//     * @param id        the image ID
//     * @return element  the "ImageSet" element
//     */
//    protected Element getImageSetElement(int id){
//        if(doc != null){
//            Element result = null;
//            int totalImageSet = getImageSetCount();
//            
//            for(int i = 0; i < totalImageSet; i++){
//                Element imageSetElement = (Element)getImagesElement().getElementsByTagName(IMG_SET_NAME).item(i);
//                int imgSetID = Integer.parseInt(imageSetElement.getAttribute(IMG_SET_ID));
//                if(id == imgSetID){
//                    result = imageSetElement;
//                }
//            }
//            return result;
//        }
//        else{
//            return null; //TODO: should throw error or exception
//        }
//    }
//
//    /**
//     * Sets the ImgDb entry for corresponding image set in the document handler
//     *
//     * @param id    the image ID
//     * @param path  the image path
//     */
//    protected void setImageSetDbPath(int id, String path){
//        Element imgDbElement = (Element) getImageSetElement(id).getElementsByTagName(IMG_DB_NAME).item(0);
//        imgDbElement.setTextContent(path);
//        imgDbElement.setAttribute(RELATIVE_NAME, RELATIVE_TRUE); // depends on the path (relative or not)
//        
//        // if need to write the XML file immidiately, uncomment the code below
//        // writeFile();
//    }
//
    /**
     * Returns number of image sets in this case
     *
     * @return imageSetCount  total number of imageSet in this case
     */
    protected int getImageSetCount(){
        return getImagesElement().getElementsByTagName(IMG_SET_NAME).getLength();
    }
//
//    /**
//     * Returns a set of image paths of the given image ID in this case
//     *
//     * @param id           the image ID
//     * @return imagePaths  the image paths of the given imageID
//     */
//    protected String[] getImageSet(int id){
//        Element imgSetElement = getImageSetElement(id);
//        int totalImagePaths = imgSetElement.getElementsByTagName(IMG_PATH_NAME).getLength(); // or use the attribute count
//        String[] result = new String[totalImagePaths];
//
//        for(int i = 0; i < totalImagePaths; i++){
//            Element imgPathElement = (Element) imgSetElement.getElementsByTagName(IMG_PATH_NAME).item(i);
//            if(imgPathElement.getAttribute(RELATIVE_NAME).equals(RELATIVE_TRUE)){
//                result[i] = caseDirPath + File.separator + imgPathElement.getTextContent();
//            }
//            else{
//                result[i] = imgPathElement.getTextContent();
//            }
//        }
//        return result;
//    }
//
//    /**
//     * Returns absolute path of image database of the given image ID.
//     *
//     * @param id       the image ID
//     * @return dbPath  the database path of the given image
//     */
//    protected String getImageSetDbPath(int id){
//        Element imgDbElement = (Element) getImageSetElement(id).getElementsByTagName(IMG_DB_NAME).item(0);
//        if(imgDbElement.getAttribute(RELATIVE_NAME).equals(RELATIVE_TRUE)){
//            return caseDirPath + File.separator + imgDbElement.getTextContent();
//        }
//        else{
//            return imgDbElement.getTextContent();
//        }
//    }
//
//    /**
//     * Gets the next free image ID and and increments the internal counter
//     *
//     * @return imageID  the next free image ID
//     */
//    @Override
//    public int getNextImageID(){
//        Element imgElement = getImagesElement();
//        if(imgElement == null){
//            return -1; // which indicates the error
//        }
//        else{
//            String nextID = imgElement.getAttribute(NEXT_ID_NAME);
//            int result = Integer.parseInt(nextID);
//
//            // increment the nextID
//            int incNextID = result + 1;
//            getImagesElement().setAttribute(NEXT_ID_NAME, Integer.toString(incNextID));
//
//            // write the change to the config file immediately
//            try{ 
//                writeFile();
//            }
//            catch(Exception ex){
//                // TODO: throw exception further up
//                Logger logger = Logger.getLogger(this.className);
//                logger.log(Level.WARNING, "Error while trying to write the new NextID to the config file.", ex);
//            }
//            
//            return result;
//        }
//    }
//
//    /**
//     * Returns an array of all image ID values in this case.
//     *
//     * @return imageIDs  array of all image IDs in this case
//     */
//    @Override
//    public int[] getImageIDs() {
//        int totalImageSet = getImageSetCount();
//        int[] result = new int[totalImageSet];
//
//        Element imgElement = getImagesElement();
//        for(int i = 0; i < totalImageSet; i++){
//            Element imgSetElement = (Element)imgElement.getElementsByTagName(IMG_SET_NAME).item(i);
//            result[i] = Integer.parseInt(imgSetElement.getAttribute(IMG_SET_ID));
//        }
//
//        return result;
//    }
//
//    /**
//     * Returns the HashSet of all timezone values of all images in this case.
//     *
//     * @return imageIDs  the HashSet of all timezone values
//     */
    public HashSet<TimeZone> getTimeZone() {
        HashSet<TimeZone> result = new HashSet<TimeZone>();

        int totalImageSet = getImageSetCount();
        Element imgElement = getImagesElement();
        for(int i = 0; i < totalImageSet; i++){
            Element imgSetElement = (Element)imgElement.getElementsByTagName(IMG_SET_NAME).item(i);
            result.add(TimeZone.getTimeZone(imgSetElement.getAttribute(TIMEZONE)));
        }

        return result; //TODO: get time zones from db
    }
//
//    /**
//     * Removes the imageSet of the given image ID.
//     *
//     * @param imgID  the image ID of the image that going to be removed
//     */
//    public void removeImageSet(int imgID) {
//            Element imgElement = getImagesElement();
//            Element selectedElement = getImageSetElement(imgID);
//            imgElement.removeChild(selectedElement); // remove the imagesetElement
//            doc.normalize();
//
//            // edit the modified data
//            String newDate = dateFormat.format(new Date());
//            Element rootEl = getRootElement();
//            rootEl.getElementsByTagName(MODIFIED_DATE_NAME).item(0).setTextContent(newDate);
//    }

    /**
     * Initialize the basic values for a new case management file.
     * Note: this is the schema version 1.0
     *
     * @param parentPath  the name of the parent of the case directory.
     * @param caseName    the name of the config file to be located in the case directory
     */
    protected void create(String dirPath, String caseName, String examiner, String caseNumber) throws Exception {
        clear(); // clear the previous data

        // set the case Name and Directory and the parent directory
        setCaseDirPath(dirPath);
        setName(caseName);
        setExaminer(examiner);
        setNumber(caseNumber);
        DocumentBuilder docBuilder;
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();

        // throw an error here
        try {
             docBuilder = docFactory.newDocumentBuilder();
        } catch (Exception ex) {
            clear();
            throw ex;
        }

        doc = docBuilder.newDocument();
        Element rootElement = doc.createElement(TOP_ROOT_NAME); // <AutopsyCase> ... </AutopsyCase>
        doc.appendChild(rootElement);

        Element crDateElement = doc.createElement(CREATED_DATE_NAME); // <CreatedDate> ... </CreatedDate>
        crDateElement.appendChild(doc.createTextNode(dateFormat.format(new Date())));
        rootElement.appendChild(crDateElement);

        Element mDateElement = doc.createElement(MODIFIED_DATE_NAME); // <ModifedDate> ... </ModifedDate>
        mDateElement.appendChild(doc.createTextNode(dateFormat.format(new Date())));
        rootElement.appendChild(mDateElement);

        Element autVerElement = doc.createElement(AUTOPSY_CRVERSION_NAME); // <AutopsyVersion> ... </AutopsyVersion>
        autVerElement.appendChild(doc.createTextNode(autopsySavedVersion));
        rootElement.appendChild(autVerElement);

        Element autSavedVerElement = doc.createElement(AUTOPSY_MVERSION_NAME); // <AutopsySavedVersion> ... </AutopsySavedVersion>
        autSavedVerElement.appendChild(doc.createTextNode(autopsySavedVersion));
        rootElement.appendChild(autSavedVerElement);

        Element schVerElement = doc.createElement(SCHEMA_VERSION_NAME); // <SchemaVersion> ... </SchemaVersion>
        schVerElement.appendChild(doc.createTextNode(schemaVersion));
        rootElement.appendChild(schVerElement);

        Element caseElement = doc.createElement(CASE_ROOT_NAME); // <Case> ... </Case>
        rootElement.appendChild(caseElement);

        Element nameElement = doc.createElement(NAME); // <Name> ... </Name>
        nameElement.appendChild(doc.createTextNode(caseName));
        caseElement.appendChild(nameElement);
        
        Element numberElement = doc.createElement(NUMBER); // <Number> ... </Number>
        numberElement.appendChild(doc.createTextNode(String.valueOf(caseNumber)));
        caseElement.appendChild(numberElement);
        
        Element examinerElement = doc.createElement(EXAMINER); // <Examiner> ... </Examiner>
        examinerElement.appendChild(doc.createTextNode(examiner));
        caseElement.appendChild(examinerElement);

        Element exportElement = doc.createElement(EXPORT_FOLDER_NAME); // <ExportFolder> ... </ExportFolder>
        exportElement.appendChild(doc.createTextNode(EXPORT_FOLDER_RELPATH));
        exportElement.setAttribute(RELATIVE_NAME, "true");
        caseElement.appendChild(exportElement);

        Element logElement = doc.createElement(LOG_FOLDER_NAME); // <LogFolder> ... </LogFolder>
        logElement.appendChild(doc.createTextNode(LOG_FOLDER_RELPATH));
        logElement.setAttribute(RELATIVE_NAME, "true");
        caseElement.appendChild(logElement);

        Element tempElement = doc.createElement(TEMP_FOLDER_NAME); // <TempFolder> ... </TempFolder>
        tempElement.appendChild(doc.createTextNode(TEMP_FOLDER_RELPATH));
        tempElement.setAttribute(RELATIVE_NAME, "true");
        caseElement.appendChild(tempElement);

        // create the new images
        Element imagesElement = doc.createElement(IMAGES_NAME); // <Images> ... </Images>
        imagesElement.setAttribute(NEXT_ID_NAME, Integer.toString(0)); // add nextID to 0
        rootElement.appendChild(imagesElement);

        // write more code if needed ...
    }

    /**
     * Writes the case management file to disk (from document handler to .aut file)
     *
     */
    @Override
    public void writeFile() throws Exception {
        if (doc == null || caseName.equals("")) {
            throw new Exception("No set case to write management file for.");
        }

        // Prepare the DOM document for writing
        Source source = new DOMSource(doc);

        // Prepare the data for the output file
        StringWriter sw = new StringWriter();
        Result result = new StreamResult(sw);

        // Write the DOM document to the file
        Transformer xformer;// = TransformerFactory.newInstance().newTransformer();
        TransformerFactory tfactory = TransformerFactory.newInstance();

        try {
            xformer = tfactory.newTransformer();
        } catch (Exception ex) {
            throw ex;
        }

        //Setup indenting to "pretty print"
        xformer.setOutputProperty(OutputKeys.INDENT, "yes");
        xformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        try {
            xformer.transform(source, result);
        } catch (TransformerException ex) {
            throw ex;
        }

        // preparing the output file
        String xmlString = sw.toString();
        File file = new File(caseDirPath + File.separator + caseName + ".aut");

        // write the file
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
            bw.write(xmlString);
            bw.flush();
            bw.close();
        } catch (Exception ex) {
            throw ex;
        }
    }

    /**
     * Opens the configuration file and load the document handler
     * Note: this is for the schema version 1.0
     *
     * @param conFilePath  the path of the XML case configuration file path
     */
    @Override
    public void open(String conFilePath) throws Exception{
        clear();
        File file = new File(conFilePath);
        
        try{
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(file);
            doc.getDocumentElement().normalize();

            Element rootEl = doc.getDocumentElement();
            String rootName = rootEl.getNodeName();

            // check if it's the autopsy case, if not, throws an error
            if(!rootName.equals(TOP_ROOT_NAME)){
                // throw an error ...
                clear();
                JOptionPane.showMessageDialog(caller, "Error: This is not an Autopsy config file (\"" + file.getName() + "\").\n \nDetail: \nCannot open a non-Autopsy config file (at " + className + ").", "Error", JOptionPane.ERROR_MESSAGE);
            }
            else{
                /* Autopsy Created Version */
                String createdVersion = getCreatedVersion(); // get the created version

                // check if it has the same autopsy version as the current one
                if(!createdVersion.equals(autopsySavedVersion)){
                    // if not the same version, update the saved version in the xml to the current version
                    getRootElement().getElementsByTagName(AUTOPSY_MVERSION_NAME).item(0).setTextContent(autopsySavedVersion);
                }

                /* Schema Version */
                String schemaVer = getSchemaVersion();
                // check if it has the same schema version as the current one
                if(!schemaVer.equals(schemaVersion)){
                    // do something here if not the same version
                    // ... @Override
                }

                // set the case Directory and Name
                setCaseDirPath(file.getParent());
                String fullFileName = file.getName();
                String fileName = fullFileName.substring(0, fullFileName.indexOf(".")); // remove the extension
                setName(fileName);
            }
        }
        catch(Exception e){
            throw e;
            // throw an error here
            //JOptionPane.showMessageDialog(caller, "Error: This file is not supported (\"" + file.getName() + "\").\n \nDetail: \n" + e.getMessage() + " (at " + className + ")." , "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * When user wants to close the case. This method writes any changes to the
     * XML case configuration file, closes it and the document handler, and
     * clears all the local variables / fields.
     *
     */
    @Override
    public void close() throws Exception {
        try {
            writeFile(); // write any changes to xml
        } catch (Exception ex) {
            throw new Exception("Error: error while trying to close XML config file.", ex);
        }

        clear();
    }

    /**
     * Clear the internal structures / variables
     */
    private void clear() {
        doc = null;
        caseDirPath = "";
        caseName = "";
        caseNumber = "";
        examiner = "";
    }

    /**
     * Adds the image to the current document handler.
     *
     * @param images     the paths of the images that want to be added
     * @param currentID  the ID of the image that want to be added
     * @param timeZone   the timeZone where the image is added
     */
    protected void addImage(String[] images, long currentID, String timeZone) throws Exception {

        //String dbName = "image-" + currentID + ".db";

        if (doc == null) {
            throw new Exception("No case open.");
        } else {
            Element rootEl = getRootElement();

            int imgCount = images.length; // how many given images to add to the config file
            Element imagesElement = getImagesElement();

            //int currentID = Integer.parseInt(imagesElement.getAttribute(NEXT_ID_NAME));

            // add the latest image set
            Element imgSetElement = doc.createElement(IMG_SET_NAME); // <ImgSet> ... </ImgSet>
            imgSetElement.setAttribute(IMG_SET_COUNT, Integer.toString(imgCount));
            imgSetElement.setAttribute(IMG_SET_ID, Long.toString(currentID));
            imgSetElement.setAttribute(TIMEZONE, timeZone);
            imagesElement.appendChild(imgSetElement);

            // add all the image Path
//            for (int i = 0; i < imgCount; i++) {
//                Element imgPathElement = doc.createElement(IMG_PATH_NAME); // <ImgPath> ... </ImgPath>
//                imgPathElement.setAttribute(IMG_PATH_ID, Integer.toString(i));
//                imgPathElement.setAttribute(RELATIVE_NAME, "false");
//                imgPathElement.appendChild(doc.createTextNode(images[i]));
//                imgSetElement.appendChild(imgPathElement);
//            }
//
//            // add the database
//            Element imgDbElement = doc.createElement(IMG_DB_NAME); // <ImgDb> ... </ImgDb>
//            imgDbElement.setAttribute(RELATIVE_NAME, "true");
//            imgDbElement.appendChild(doc.createTextNode(dbName));
//            //imgDbElement.appendChild(doc.createTextNode("image-" + currentID + ".db"));
//            imgSetElement.appendChild(imgDbElement);

            // edit the modified data
            String newDate = dateFormat.format(new Date());
            rootEl.getElementsByTagName(MODIFIED_DATE_NAME).item(0).setTextContent(newDate);
        }

//            JOptionPane.showMessageDialog(caller, "Error while trying to add the image to XML config file.\n \nDetail: \n" + e.getMessage() + " (at " + className + ").", "Error", JOptionPane.ERROR_MESSAGE);

    }
}

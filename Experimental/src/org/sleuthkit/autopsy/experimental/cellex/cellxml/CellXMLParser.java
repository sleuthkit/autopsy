/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.cellex.cellxml;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractContent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 *
 */
public class CellXMLParser {

    private static final Logger logger = Logger.getLogger(CellXMLParser.class.getName());
    private static CellXMLParser defaultInstance = null;

    private static int m_eventLogArtifactID = -1;
    public static final String EVENT_LOG_ARTIFACT_NAME = "EVENT_LOG_ENTRY";
    public static final String EVENT_LOG_ARTIFACT_DISPLAY_NAME = "Event Log Entry";

    public static final String DATETIME_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ssX";  // Note: the trailing X that parses the 8601 specification of timezone is available only from Java 7 onwards

    public CellXMLParser() {

        CreatePrivateArtifactsAttributes();
    }

    public static synchronized CellXMLParser getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new CellXMLParser();
        }
        return defaultInstance;
    }

    private void CreatePrivateArtifactsAttributes() {
        /**
         * **
         *
         * if (-1 == m_eventLogArtifactID) {
         *
         * try { m_eventLogArtifactID =
         * services.getCurrentSleuthkitCaseDb().addArtifactType(EVENT_LOG_ARTIFACT_NAME,
         * EVENT_LOG_ARTIFACT_DISPLAY_NAME); logger.log(Level.INFO,
         * "CreatePrivateArtifactsAttributes: Private Artifact: " +
         * EVENT_LOG_ARTIFACT_NAME + " added successfully!");
         *
         *
         * }catch (TskCoreException e) { //error reading file
         * logger.log(Level.SEVERE, "CreatePrivateArtifactsAttributes: Failed to
         * create private artifact: " + EVENT_LOG_ARTIFACT_NAME + " Error = ",
         * e);
         *
         * }
         * }
         * else { logger.log(Level.INFO, "CreatePrivateArtifactsAttributes:
         * artifact: " + EVENT_LOG_ARTIFACT_NAME + " already defined. ID = ",
         * m_eventLogArtifactID);
         *
         * }
         *
         * return; ***
         */

    }

    public void Process(String cellXMLInputFilePath, AbstractContent content, String aModuleName) {

        if (cellXMLInputFilePath == null || cellXMLInputFilePath.isEmpty()) {
            return;
        }

        logger.log(Level.FINER, "Process():  Will process CellXML file: " + cellXMLInputFilePath);

        java.io.File cellxmlFile = new java.io.File(cellXMLInputFilePath);
        if (cellxmlFile.exists()) {
            Document cllxmlDoc = LoadCLLXML(cellXMLInputFilePath);
            if (null != cllxmlDoc) {
                ParseCellXML(cllxmlDoc, content, aModuleName);
            } else {
                logger.log(Level.SEVERE, "LoadCLLXML() failed.");
            }
        } else {
            logger.log(Level.SEVERE, "CellXML file: " + cellXMLInputFilePath + " not found.");
        }

        return;

    }

    // Loads the CLLXML file into a doc
    private Document LoadCLLXML(String xmlFilePath) {

        Document doc = null;

        // Ideally we should use XMLUtil to read in the XML but currently 
        // it doesnt read files without needing a schema to validate against
        DocumentBuilderFactory builderFactory
                = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            doc = builder.parse(new FileInputStream(xmlFilePath));

            doc.getDocumentElement().normalize();

        } catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error loading XML file: " + xmlFilePath + " Can't initialize parser.", e);

        } catch (SAXException e) {
            logger.log(Level.SEVERE, "Error loading XML file: " + xmlFilePath + " Can't parse XML.", e);

        } catch (IOException e) {
            //error reading file
            logger.log(Level.SEVERE, "Error loading XML file: " + xmlFilePath + " Can't read file.", e);

        }

        if (null != doc) {
            logger.log(Level.INFO, "CLLXML file: " + xmlFilePath + " loaded successfully!!.");
        }

        return doc;
    }

    // Process the CLLXML Output produced by MPF and 
    // create BlackBoard entries from the data
    private void ParseCellXML(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        if (null == aCLLXMLDoc) {
            return;
        }

        ProcessContacts(aCLLXMLDoc, abstractContent, aModuleName);
        ProcessCalls(aCLLXMLDoc, abstractContent, aModuleName);
        ProcessMessages(aCLLXMLDoc, abstractContent, aModuleName);
        ProcessCalendarEntries(aCLLXMLDoc, abstractContent, aModuleName);

        ProcessInternetBookmarks(aCLLXMLDoc, abstractContent, aModuleName);
        ProcessInternetHistory(aCLLXMLDoc, abstractContent, aModuleName);
        ProcessCookies(aCLLXMLDoc, abstractContent, aModuleName);

        ProcessSpeedDialEntries(aCLLXMLDoc, abstractContent, aModuleName);

        ProcessBluetoothEntries(aCLLXMLDoc, abstractContent, aModuleName);

        //ProcessEventLogEntries(aCLLXMLDoc, abstractContent);
        ProcessGPSFavorites(aCLLXMLDoc, abstractContent, aModuleName);
        ProcessGPSSearches(aCLLXMLDoc, abstractContent, aModuleName);
        ProcessGPSLastKnownLocation(aCLLXMLDoc, abstractContent, aModuleName);

        ProcessApplicationAccounts(aCLLXMLDoc, abstractContent, aModuleName);

    }

    private long GetSecsSinceEpochFrom8601TimeStamp(String aTimeStamp) {
        if (null == aTimeStamp) {
            logger.log(Level.WARNING, "GetSecsSinceEpochFrom8601TimeStamp(): aTimeStamp is null!");
            return 0;
        }

        SimpleDateFormat ISO8601DATEFORMAT = new SimpleDateFormat(DATETIME_FORMAT_STRING);
        ISO8601DATEFORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date date = ISO8601DATEFORMAT.parse(aTimeStamp);
            long millisecsEpoch = date.getTime();
            long secsEpoch = millisecsEpoch / 1000;

            return secsEpoch;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "GetSecsSinceEpochFrom8601TimeStamp: Failed to parse 8601 timestamp string. (" + ex.getLocalizedMessage() + ").");
            return 0;
        }

    }

    private static String GetXMLElemValue(String tag, Element element) {

        //logger.log(Level.INFO, "GetXMLElemValue(): Looking for tag :  " + tag );
        String retStr = null;
        NodeList nodeList = element.getElementsByTagName(tag);

        if (nodeList.getLength() > 0) {
            Element node = (Element) nodeList.item(0);
            //retStr = node.getNodeValue();
            retStr = node.getTextContent();
            if (null == retStr) {
                logger.log(Level.FINER, "GetXMLElemValue(): getNodeValue() returned NULL for tag = " + tag);
            }
        }

        return retStr;
    }

    /*
     * If the given node has a "deleted" flag, create a TSK_ISDELETED attribute
     * and append to the given set of attributes
     */
    private boolean FlagDeletedContent(Element node, Collection<BlackboardAttribute> attributes, String aModuleName) {
        String deletedFlag = node.getAttribute(CellXMLDef.CLLXML_DELETED_ATTR);
        if (null != deletedFlag) {
            if (deletedFlag.equalsIgnoreCase("true")) {
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ISDELETED, aModuleName, "yes"));
                return true;
            }
        }

        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ISDELETED, aModuleName, ""));
        return false;
    }

    private void ProcessContacts(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList contactnodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_CONTACT_TAG);

        int len = contactnodes.getLength();
        logger.log(Level.INFO, "ProcessContacts(): Found  " + Integer.toString(len) + " contacts !!.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

            Element contactNode = (Element) contactnodes.item(c);

            String idValue = contactNode.getAttribute(CellXMLDef.CLLXML_UUID_ATTR);

            //logger.log(Level.FINER, "ProcessContacts(): Found  Contact with UUID : " + idValue );
            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(contactNode, attributes, aModuleName);

            // Get the name
            String name = null;
            NodeList nameNodes = contactNode.getElementsByTagName(CellXMLDef.CLLXML_NAME_TAG);
            if (nameNodes.getLength() > 0) {
                Element nameNode = (Element) nameNodes.item(0);

                name = GetXMLElemValue(CellXMLDef.CLLXML_DISPLAYNAME_TAG, nameNode);
                if (null != name) {
                    logger.log(Level.FINER, "ProcessContacts(): Found Name = " + name);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, aModuleName, name));
                } else {
                    logger.log(Level.FINER, "ProcessContacts(): Failed to get Name.");
                }

            }

            // Get phone numbers, there may be more than 1
            NodeList phoneNumberNodes = contactNode.getElementsByTagName(CellXMLDef.CLLXML_PHONE_NUMBER_TAG);
            for (int p = 0; p < phoneNumberNodes.getLength(); p++) {
                Element phNumberNode = (Element) phoneNumberNodes.item(p);

                String phNumType = phNumberNode.getAttribute(CellXMLDef.CLLXML_TYPE_ATTR);
                String phNum = null;

                phNum = GetXMLElemValue(CellXMLDef.CLLXML_PHONE_FORM_TAG, phNumberNode);
                if (null != phNum) {
                    logger.log(Level.FINER, "ProcessContacts(): Found Phone Num = " + phNum + " type = " + phNumType);

                    // distinguish between different types - work, home, mobile
                    BlackboardAttribute.ATTRIBUTE_TYPE attrType;

                    if (phNumType.equalsIgnoreCase(CellXMLDef.CLLXML_TYPE_HOME)) {
                        attrType = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME;
                    } else if (phNumType.equalsIgnoreCase(CellXMLDef.CLLXML_TYPE_WORK)) {
                        attrType = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_OFFICE;
                    } else if (phNumType.equalsIgnoreCase(CellXMLDef.CLLXML_TYPE_MOBILE)) {
                        attrType = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE;
                    } else {
                        attrType = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER;
                    }

                    attributes.add(new BlackboardAttribute(attrType, aModuleName, phNum));

                } else {
                    logger.log(Level.FINER, "ProcessContacts(): Failed to get phoneForm.");
                }
            }

            // Get email addresses, there may be more than 1
            NodeList emailAddrNodes = contactNode.getElementsByTagName(CellXMLDef.CLLXML_EMAIL_ADDRESS_TAG);
            for (int e = 0; e < emailAddrNodes.getLength(); e++) {
                Element emailAddrNode = (Element) emailAddrNodes.item(e);

                String emailType = emailAddrNode.getAttribute(CellXMLDef.CLLXML_TYPE_ATTR);
                String email = null;

                email = GetXMLElemValue(CellXMLDef.CLLXML_RFC5322_FORM_TAG, emailAddrNode);
                if (null != email) {
                    logger.log(Level.FINER, "ProcessContacts(): Found Email addr = " + email + " type = " + emailType);

                    // distinguish between different types - work, home, mobile
                    BlackboardAttribute.ATTRIBUTE_TYPE attrType;
                    if (emailType.equalsIgnoreCase(CellXMLDef.CLLXML_TYPE_HOME)) {
                        attrType = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_HOME;
                    } else if (emailType.equalsIgnoreCase(CellXMLDef.CLLXML_TYPE_WORK)) {
                        attrType = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_OFFICE;
                    } else {
                        attrType = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL;
                    }

                    attributes.add(new BlackboardAttribute(attrType, aModuleName, email));

                } else {
                    logger.log(Level.FINER, "ProcessContacts(): Failed to get Email.");
                }
            }

            try {

                // Create a CONTACT Artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to create blackboard artifact for Contact. (" + ex.getLocalizedMessage() + ").");
            }

        } // for each contact

    }

    private void ProcessSpeedDialEntries(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList speedDialEntryNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_SPEEDDIAL_ENTRY_TAG);

        int len = speedDialEntryNodes.getLength();
        logger.log(Level.INFO, "ProcessSpeedDialEntries(): Found  " + Integer.toString(len) + " SpeedDial entries !!.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

            Element speedDialEntryNode = (Element) speedDialEntryNodes.item(c);

            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(speedDialEntryNode, attributes, aModuleName);

            // get dialCode
            String dialCode = null;
            dialCode = GetXMLElemValue(CellXMLDef.CLLXML_DIALCODE_TAG, speedDialEntryNode);
            if (null != dialCode) {
                logger.log(Level.FINER, "ProcessSpeedDialEntries(): Found dialCode = " + dialCode);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SHORTCUT, aModuleName, dialCode));
            } else {
                logger.log(Level.FINER, "ProcessSpeedDialEntries(): Failed to get dialCode.");
            }

            // Get the display name, if there is one
            String name = null;
            name = GetXMLElemValue(CellXMLDef.CLLXML_DISPLAYNAME_TAG, speedDialEntryNode);
            if (null != name) {
                logger.log(Level.FINER, "ProcessSpeedDialEntries(): Found Name = " + name);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME_PERSON, aModuleName, name));
            } else {
                logger.log(Level.FINER, "ProcessSpeedDialEntries(): Failed to get Name.");
            }

            // Get phone number
            NodeList phoneNumberNodes = speedDialEntryNode.getElementsByTagName(CellXMLDef.CLLXML_PHONE_NUMBER_TAG);
            if (phoneNumberNodes.getLength() > 0) {
                String phNum = null;

                Element phNumberNode = (Element) phoneNumberNodes.item(0);
                phNum = GetXMLElemValue(CellXMLDef.CLLXML_PHONE_FORM_TAG, phNumberNode);
                if (null != phNum) {
                    logger.log(Level.FINER, "ProcessSpeedDialEntries(): Found Phone Num = " + phNum);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, aModuleName, phNum));

                } else {
                    logger.log(Level.FINER, "ProcessSpeedDialEntries(): Failed to get phoneForm.");
                }
            }

            try {
                // Create a SpeedDial Entry Artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_SPEED_DIAL_ENTRY);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "ProcessSpeedDialEntries(): Failed to create blackboard artifact for Cookie. (" + ex.getLocalizedMessage() + ").");
            }

        } // for each speed dial entry

    }

    private void ProcessCalls(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList callnodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_CALL_TAG);

        int len = callnodes.getLength();
        logger.log(Level.INFO, "ProcessCalls(): Found  " + Integer.toString(len) + " calls !!.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

            Element callNode = (Element) callnodes.item(c);

            // String idValue = callNode.getAttribute(CellXMLDef.CLLXML_UUID_ATTR); 
            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(callNode, attributes, aModuleName);

            // Get the display name, if there is one
            String name = null;
            name = GetXMLElemValue(CellXMLDef.CLLXML_DISPLAYNAME_TAG, callNode);
            if (null != name) {
                logger.log(Level.FINER, "ProcessCalls(): Found Name = " + name);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, aModuleName, name));
            } else {
                logger.log(Level.FINER, "ProcessCalls(): Failed to get Name.");
            }

            // Get phone number
            NodeList phoneNumberNodes = callNode.getElementsByTagName(CellXMLDef.CLLXML_PHONE_NUMBER_TAG);
            if (phoneNumberNodes.getLength() > 0) {
                String phNum = null;

                Element phNumberNode = (Element) phoneNumberNodes.item(0);
                phNum = GetXMLElemValue(CellXMLDef.CLLXML_PHONE_FORM_TAG, phNumberNode);
                if (null != phNum) {
                    logger.log(Level.FINER, "ProcessCalls(): Found Phone Num = " + phNum);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, aModuleName, phNum));

                } else {
                    logger.log(Level.FINER, "ProcessCalls(): Failed to get phoneForm.");
                }
            }

            // Get timestamp
            NodeList timeNodes = callNode.getElementsByTagName(CellXMLDef.CLLXML_TIME_TAG);
            if (timeNodes.getLength() > 0) {
                String timeStampStr = null;

                Element timeNode = (Element) timeNodes.item(0);
                timeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, timeNode);
                if (null != timeStampStr) {
                    logger.log(Level.FINER, "ProcessCalls(): Found timeStamp = " + timeStampStr);

                    long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(timeStampStr);
                    if (secsEpoch > 0) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START, aModuleName, secsEpoch));
                    }

                } else {
                    logger.log(Level.FINER, "ProcessCalls(): Failed to get timeStamp.");
                }
            }

            // get direction
            String direction = null;
            direction = GetXMLElemValue(CellXMLDef.CLLXML_DIRECTION_TAG, callNode);
            if (null != direction) {
                logger.log(Level.FINER, "ProcessCalls(): Found direction = " + direction);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, aModuleName, direction));
            } else {
                logger.log(Level.FINER, "ProcessCalls(): Failed to get direction.");
            }

            try {

                // Create a Calllog Artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to create blackboard artifact for Call. (" + ex.getLocalizedMessage() + ").");
            }

        } // for each call

    }

    private void ProcessMessages(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList messagenodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_MESSAGE_TAG);

        int len = messagenodes.getLength();
        logger.log(Level.INFO, "ProcessMessages(): Found  " + Integer.toString(len) + " messages !!.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

            Element messageNode = (Element) messagenodes.item(c);

            // String idValue = callNode.getAttribute(CellXMLDef.CLLXML_UUID_ATTR); 
            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(messageNode, attributes, aModuleName);

            String msgType = messageNode.getAttribute(CellXMLDef.CLLXML_TYPE_ATTR);
            if (null != msgType) {
                logger.log(Level.FINER, "ProcessMessages(): Found message of type = " + msgType);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, aModuleName, msgType));

            }

            // get direction
            String direction = null;
            direction = GetXMLElemValue(CellXMLDef.CLLXML_DIRECTION_TAG, messageNode);
            if (null != direction) {
                logger.log(Level.FINER, "ProcessMessages(): Found direction = " + direction);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, aModuleName, direction));
            } else {
                logger.log(Level.FINER, "ProcessMessages(): Failed to get direction.");
            }

            // Get source phonenumber/email
            NodeList sourceNodes = messageNode.getElementsByTagName(CellXMLDef.CLLXML_SOURCE_TAG);
            if (sourceNodes.getLength() > 0) {
                Element sourceNode = (Element) sourceNodes.item(0);

                // Get source phone number
                NodeList phoneNumberNodes = sourceNode.getElementsByTagName(CellXMLDef.CLLXML_PHONE_NUMBER_TAG);
                if (phoneNumberNodes.getLength() > 0) {
                    String phNum = null;

                    Element phNumberNode = (Element) phoneNumberNodes.item(0);
                    phNum = GetXMLElemValue(CellXMLDef.CLLXML_PHONE_FORM_TAG, phNumberNode);
                    if (null != phNum) {
                        logger.log(Level.FINER, "ProcessMessages(): Found Phone Num = " + phNum);
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, aModuleName, phNum));

                    } else {
                        logger.log(Level.FINER, "ProcessMessages(): Failed to get phoneForm.");
                    }
                }

                // Get source email
                NodeList emailNodes = sourceNode.getElementsByTagName(CellXMLDef.CLLXML_EMAIL_ADDRESS_TAG);
                if (emailNodes.getLength() > 0) {
                    String emailAddr = null;

                    Element emailNode = (Element) emailNodes.item(0);
                    emailAddr = GetXMLElemValue(CellXMLDef.CLLXML_RFC5322_FORM_TAG, emailNode);
                    if (null != emailAddr) {
                        logger.log(Level.FINER, "ProcessMessages(): Found Source email addr = " + emailAddr);
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM, aModuleName, emailAddr));

                    } else {
                        logger.log(Level.FINER, "ProcessMessages(): Failed to get source email.");
                    }
                }

            }

            // Get desination phonenumber/email
            NodeList destinationNodes = messageNode.getElementsByTagName(CellXMLDef.CLLXML_DESTINATION_TAG);
            if (destinationNodes.getLength() > 0) {
                Element destNode = (Element) destinationNodes.item(0);

                // Get destination phone number
                NodeList phoneNumberNodes = destNode.getElementsByTagName(CellXMLDef.CLLXML_PHONE_NUMBER_TAG);
                if (phoneNumberNodes.getLength() > 0) {
                    String phNum = null;

                    Element phNumberNode = (Element) phoneNumberNodes.item(0);
                    phNum = GetXMLElemValue(CellXMLDef.CLLXML_PHONE_FORM_TAG, phNumberNode);
                    if (null != phNum) {
                        logger.log(Level.FINER, "ProcessMessages(): Found Phone Num = " + phNum);
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, aModuleName, phNum));

                    } else {
                        logger.log(Level.FINER, "ProcessMessages(): Failed to get phoneForm.");
                    }
                }

                // Get destination email
                NodeList emailNodes = destNode.getElementsByTagName(CellXMLDef.CLLXML_EMAIL_ADDRESS_TAG);
                if (emailNodes.getLength() > 0) {
                    String emailAddr = null;

                    Element emailNode = (Element) emailNodes.item(0);
                    emailAddr = GetXMLElemValue(CellXMLDef.CLLXML_RFC5322_FORM_TAG, emailNode);
                    if (null != emailAddr) {
                        logger.log(Level.FINER, "ProcessMessages(): Found dest email addr = " + emailAddr);
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO, aModuleName, emailAddr));

                    } else {
                        logger.log(Level.FINER, "ProcessMessages(): Failed to dest email.");
                    }
                }
            }

            // Get timestamp
            NodeList timeNodes = messageNode.getElementsByTagName(CellXMLDef.CLLXML_TIME_TAG);
            if (timeNodes.getLength() > 0) {
                String timeStampStr = null;

                Element timeNode = (Element) timeNodes.item(0);
                timeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, timeNode);
                if (null != timeStampStr) {
                    logger.log(Level.FINER, "ProcessMessages(): Found timeStamp = " + timeStampStr);

                    long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(timeStampStr);
                    if (secsEpoch > 0) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, aModuleName, secsEpoch));
                    }

                } else {
                    logger.log(Level.FINER, "ProcessMessages(): Failed to get timeStamp.");
                }
            }

            // get subject
            String subject = null;
            subject = GetXMLElemValue(CellXMLDef.CLLXML_SUBJECT_TAG, messageNode);
            if (null != subject) {
                logger.log(Level.FINER, "ProcessMessages(): Found subject = " + subject);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT, aModuleName, subject));
            } else {
                logger.log(Level.FINER, "ProcessMessages(): Failed to get subject.");
            }

            // get body
            String msgBody = null;
            msgBody = GetXMLElemValue(CellXMLDef.CLLXML_BODY_TAG, messageNode);
            if (null != msgBody) {
                logger.log(Level.FINER, "ProcessMessages(): Found msgBody = " + msgBody);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT, aModuleName, msgBody));
            } else {
                logger.log(Level.FINER, "ProcessMessages(): Failed to get msg body.");
            }

            try {

                // Create a Message Artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "ProcessMessages(): Failed to create blackboard artifact for Message. (" + ex.getLocalizedMessage() + ").");
            }

        } // for each message

    }

    private void ProcessCalendarEntries(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList calendarNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_CALENDAR_ENTRY_TAG);

        int len = calendarNodes.getLength();
        logger.log(Level.INFO, "ProcessCalendarEntries(): Found  " + Integer.toString(len) + " calendar entries !!.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

            Element calNode = (Element) calendarNodes.item(c);

            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(calNode, attributes, aModuleName);

            // String idValue = callNode.getAttribute(CellXMLDef.CLLXML_UUID_ATTR); 
            String calType = calNode.getAttribute(CellXMLDef.CLLXML_TYPE_ATTR);

            if (null != calType) {
                logger.log(Level.FINER, "ProcessCalendarEntries(): Found calendar entry of type = " + calType);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CALENDAR_ENTRY_TYPE, aModuleName, calType));

            }

            // get description
            String description = null;
            description = GetXMLElemValue(CellXMLDef.CLLXML_DESCRIPTION_TAG, calNode);
            if (null != description) {
                logger.log(Level.FINER, "ProcessCalendarEntries(): Found direction = " + description);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION, aModuleName, description));
            } else {
                logger.log(Level.WARNING, "ProcessCalendarEntries(): Failed to get direction.");
            }

            // Get Start time
            NodeList startTimeNodes = calNode.getElementsByTagName(CellXMLDef.CLLXML_START_TAG);
            if (startTimeNodes.getLength() > 0) {
                String startTimeStampStr = null;

                Element starttimeNode = (Element) startTimeNodes.item(0);
                startTimeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, starttimeNode);
                if (null != startTimeStampStr) {
                    logger.log(Level.FINER, "ProcessCalendarEntries(): Found start timeStamp = " + startTimeStampStr);

                    long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(startTimeStampStr);
                    if (secsEpoch > 0) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START, aModuleName, secsEpoch));
                    }

                } else {
                    logger.log(Level.WARNING, "ProcessCalendarEntries(): Failed to get start timeStamp.");
                }
            }

            // Get end time
            NodeList endTimeNodes = calNode.getElementsByTagName(CellXMLDef.CLLXML_END_TAG);
            if (endTimeNodes.getLength() > 0) {
                String endTimeStampStr = null;

                Element endtimeNode = (Element) endTimeNodes.item(0);
                endTimeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, endtimeNode);
                if (null != endTimeStampStr) {
                    logger.log(Level.FINER, "ProcessCalendarEntries(): Found end timeStamp = " + endTimeStampStr);

                    long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(endTimeStampStr);
                    if (secsEpoch > 0) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END, aModuleName, secsEpoch));
                    }
                } else {
                    logger.log(Level.WARNING, "ProcessCalendarEntries(): Failed to get end timeStamp.");
                }
            }

            try {

                // Create a Calendar entry Artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CALENDAR_ENTRY);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "ProcessCalendarEntries(): Failed to create blackboard artifact for Calendar Entry. (" + ex.getLocalizedMessage() + ").");
            }

        } // for each cal entry

    }

    private void ProcessBluetoothEntries(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList bluetoothNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_BLUETOOTH_PAIRING_TAG);

        int len = bluetoothNodes.getLength();
        logger.log(Level.INFO, "ProcessBluetoothEntries(): Found  " + Integer.toString(len) + " Bluetooth entries !!.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

            Element bluetoothEntryNode = (Element) bluetoothNodes.item(c);

            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(bluetoothEntryNode, attributes, aModuleName);

            // get deviceName
            String deviceName = null;
            deviceName = GetXMLElemValue(CellXMLDef.CLLXML_DEVICE_NAME_TAG, bluetoothEntryNode);
            if (null != deviceName) {
                logger.log(Level.FINER, "ProcessBluetoothEntries(): Found device name = " + deviceName);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_NAME, aModuleName, deviceName));
            } else {
                logger.log(Level.FINER, "ProcessBluetoothEntries(): Failed to get dialCode.");
            }

            // Get the display name, if there is one
            String deviceID = null;
            deviceID = GetXMLElemValue(CellXMLDef.CLLXML_DEVICE_ADDRESS_TAG, bluetoothEntryNode);
            if (null != deviceID) {
                logger.log(Level.FINER, "ProcessBluetoothEntries(): Found device id = " + deviceID);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID, aModuleName, deviceID));
            } else {
                logger.log(Level.FINER, "ProcessBluetoothEntries(): Failed to get deviceID.");
            }

            // Get  time
            NodeList timeNodes = bluetoothEntryNode.getElementsByTagName(CellXMLDef.CLLXML_TIME_TAG);
            if (timeNodes.getLength() > 0) {
                String timeStampStr = null;

                Element timeNode = (Element) timeNodes.item(0);
                timeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, timeNode);
                if (null != timeStampStr) {
                    logger.log(Level.FINER, "ProcessBluetoothEntries(): Found timeStamp = " + timeStampStr);

                    long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(timeStampStr);
                    if (secsEpoch > 0) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, aModuleName, secsEpoch));
                    }
                } else {
                    logger.log(Level.FINER, "ProcessBluetoothEntries(): Failed to get create timeStamp.");
                }
            }

            try {
                // Create a SpeedDial Entry Artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_BLUETOOTH_PAIRING);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "ProcessBluetoothEntries(): Failed to create blackboard artifact for Bluetooth entry. (" + ex.getLocalizedMessage() + ").");
            }

        } // for each cookie

    }

    private void ProcessEventLogEntries(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList eventLogNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_EVENTLOG_ENTRY_TAG);

        int len = eventLogNodes.getLength();
        logger.log(Level.INFO, "ProcessEventLogEntries(): Found  " + Integer.toString(len) + " EventLog entries !!.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

            Element eventLogNode = (Element) eventLogNodes.item(c);

            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(eventLogNode, attributes, aModuleName);

            String eventType = eventLogNode.getAttributes().getNamedItem("type").getNodeValue();
            if (null != eventType) {
                logger.log(Level.FINER, "ProcessApplicationAccounts(): Found eventType = " + eventType);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, aModuleName, eventType));

            } else {
                logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get userID.");
            }

            // get description
            String eventDescription = null;
            eventDescription = GetXMLElemValue(CellXMLDef.CLLXML_EVENT_DESCRIPTION_TAG, eventLogNode);
            if (null != eventDescription) {
                logger.log(Level.FINER, "ProcessEventLogEntries(): Found event description = " + eventDescription);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION, aModuleName, eventDescription));
            } else {
                logger.log(Level.FINER, "ProcessEventLogEntries(): Failed to get eventDescription.");
            }

            // Get  time
            NodeList timeNodes = eventLogNode.getElementsByTagName(CellXMLDef.CLLXML_TIME_TAG);
            if (timeNodes.getLength() > 0) {
                String timeStampStr = null;

                Element timeNode = (Element) timeNodes.item(0);
                timeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, timeNode);
                if (null != timeStampStr) {
                    logger.log(Level.FINER, "ProcessEventLogEntries(): Found timeStamp = " + timeStampStr);

                    long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(timeStampStr);
                    if (secsEpoch > 0) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, aModuleName, secsEpoch));
                    }
                } else {
                    logger.log(Level.FINER, "ProcessEventLogEntries(): Failed to get create timeStamp.");
                }
            }

            try {
                // Create a EventLog Entry Artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    // use the private artifact ID for EventLog Artifact
                    BlackboardArtifact bba = abstractContent.newArtifact(m_eventLogArtifactID);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "ProcessEventLogEntries(): Failed to create blackboard artifact for EventLog entry. (" + ex.getLocalizedMessage() + ").");
            }

        } // for each event

    }

    private void ProcessApplicationAccounts(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList appAccountsNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_APPLICATION_ACCOUNT_TAG);

        int len = appAccountsNodes.getLength();
        logger.log(Level.INFO, "ProcessApplicationAccounts(): Found  " + Integer.toString(len) + " application accounts.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();
            BlackboardAttribute userId = null;

            Element appAccountNode = (Element) appAccountsNodes.item(c);

            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(appAccountNode, attributes, aModuleName);

            // Get userAccountInfo
            NodeList userAccountInfoNodes = appAccountNode.getElementsByTagName(CellXMLDef.CLLXML_USER_ACCOUNT_INFO_TAG);
            if (userAccountInfoNodes.getLength() > 0) {
                Element userAccountInfoNode = (Element) userAccountInfoNodes.item(0);

                // get userID
                String userID = GetXMLElemValue(CellXMLDef.CLLXML_USER_ID_TAG, userAccountInfoNode);
                if (null != userID) {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Found userID = " + userID);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_ID, aModuleName, userID));

                    userId = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_ID, aModuleName, userID);

                } else {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get phoneForm.");
                }

                // get password
                String password = GetXMLElemValue(CellXMLDef.CLLXML_PASSWORD_TAG, userAccountInfoNode);
                if (null != password) {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Found password = " + password);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PASSWORD, aModuleName, password));

                } else {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get password.");
                }

                // Get displayName
                // get password
                String displayName = GetXMLElemValue(CellXMLDef.CLLXML_DISPLAYNAME_TAG, userAccountInfoNode);
                if (null != displayName) {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Found display name = " + displayName);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, aModuleName, displayName));

                } else {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get display name");
                }

            } else {
                logger.log(Level.FINER, "ProcessApplicationAccounts(): Could not find element <userAccountInfo>");
            }

            // Get applicationInfo
            NodeList applicationInfoNodes = appAccountNode.getElementsByTagName(CellXMLDef.CLLXML_APPLICATION_INFO_TAG);
            if (applicationInfoNodes.getLength() > 0) {
                Element applicationInfoNode = (Element) applicationInfoNodes.item(0);

                // get application Type - appType is an attribute
                String appType = applicationInfoNode.getAttributes().getNamedItem("type").getNodeValue();
                if (null != appType) {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Found appType = " + appType);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, aModuleName, appType));

                } else {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get userID.");
                }

                // get appName
                String appName = GetXMLElemValue(CellXMLDef.CLLXML_APPLICATION_NAME_TAG, applicationInfoNode);
                if (null != appName) {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Found appName = " + appName);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, aModuleName, appName));

                } else {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get appName.");
                }

                // get appURL
                String appURL = GetXMLElemValue(CellXMLDef.CLLXML_URL_TAG, applicationInfoNode);
                if (null != appURL) {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Found app URL = " + appURL);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL, aModuleName, appURL));

                } else {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get app URL");
                }

                // get appPath
                String appPath = GetXMLElemValue(CellXMLDef.CLLXML_INSTALL_PATH_TAG, applicationInfoNode);
                if (null != appPath) {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Found app Path = " + appPath);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH, aModuleName, appPath));

                } else {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get app Path");
                }

            } else {
                logger.log(Level.FINER, "ProcessApplicationAccounts(): Could not find element <applicationInfo>");
            }

            // Get emailAccountInfo
            NodeList emailAccountInfoNodes = appAccountNode.getElementsByTagName(CellXMLDef.CLLXML_EMAIL_ACCOUNT_INFO_TAG);
            if (applicationInfoNodes.getLength() > 0) {
                Element emailAccountInfoNode = (Element) emailAccountInfoNodes.item(0);

                // get mailboxName
                String mboxName = GetXMLElemValue(CellXMLDef.CLLXML_MAILBOX_NAME_TAG, emailAccountInfoNode);
                if (null != mboxName) {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Found mboxName = " + mboxName);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION, aModuleName, mboxName));
                } else {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get mboxName.");
                }

                // get replyToAddress
                String replyToAddress = GetXMLElemValue(CellXMLDef.CLLXML_REPLY_ADDRESS_TAG, emailAccountInfoNode);
                if (null != replyToAddress) {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Found replyToAddress = " + replyToAddress);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_REPLYTO, aModuleName, replyToAddress));
                } else {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get replyToAddress");
                }

                // get mailServer
                String serverName = GetXMLElemValue(CellXMLDef.CLLXML_MAILSERVER_TAG, emailAccountInfoNode);
                if (null != serverName) {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Found app Path = " + serverName);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SERVER_NAME, aModuleName, serverName));
                } else {
                    logger.log(Level.FINER, "ProcessApplicationAccounts(): Failed to get app Path");
                }

            } else {
                logger.log(Level.FINER, "ProcessApplicationAccounts(): Could not find element <emailAccountInfo>");
            }

            try {
                // Create a ApplicationAccount Search artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_SERVICE_ACCOUNT);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
                if (userId != null) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_ACCOUNT);
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_ID,
                            aModuleName, userId.getValueString()));
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "ProcessApplicationAccounts(): Failed to create blackboard artifact for Application Account. (" + ex.getLocalizedMessage() + ").");
            }

        } // for application account
    }

    private void ProcessCookies(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList cookieNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_COOKIE_TAG);

        int len = cookieNodes.getLength();
        logger.log(Level.INFO, "ProcessCookies(): Found  " + Integer.toString(len) + " Cookie entries !!.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

            Element cookieNode = (Element) cookieNodes.item(c);

            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(cookieNode, attributes, aModuleName);

            // get name
            String cookieName = null;
            cookieName = GetXMLElemValue(CellXMLDef.CLLXML_NAME_TAG, cookieNode);
            if (null != cookieName) {
                logger.log(Level.FINER, "ProcessCookies(): Found cookieName = " + cookieName);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, aModuleName, cookieName));
            } else {
                logger.log(Level.FINER, "ProcessCookies(): Failed to get urlName.");
            }

            // Get value
            String cookieValue = null;
            cookieValue = GetXMLElemValue(CellXMLDef.CLLXML_VALUE_TAG, cookieNode);
            if (null != cookieValue) {
                logger.log(Level.FINER, "ProcessCookies(): Found cookieValue = " + cookieValue);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE, aModuleName, cookieValue));
            } else {
                logger.log(Level.FINER, "ProcessCookies(): Failed to get cookieValue.");
            }

            // Get domain
            String domain = null;
            domain = GetXMLElemValue(CellXMLDef.CLLXML_DOMAIN_TAG, cookieNode);
            if (null != domain) {
                logger.log(Level.FINER, "ProcessCookies(): Found cookieValue = " + domain);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN, aModuleName, domain));
            } else {
                logger.log(Level.FINER, "ProcessCookies(): Failed to get domain.");
            }

            // Get path
            String path = null;
            path = GetXMLElemValue(CellXMLDef.CLLXML_PATH_TAG, cookieNode);
            if (null != path) {
                logger.log(Level.FINER, "ProcessCookies(): Found path = " + path);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH, aModuleName, path));
            } else {
                logger.log(Level.FINER, "ProcessCookies(): Failed to get domain.");
            }

            // Get created time
            NodeList createdTimeNodes = cookieNode.getElementsByTagName(CellXMLDef.CLLXML_CREATED_TAG);
            if (createdTimeNodes.getLength() > 0) {

                String createdTimeStampStr = null;

                Element starttimeNode = (Element) createdTimeNodes.item(0);
                createdTimeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, starttimeNode);
                if (null != createdTimeStampStr) {
                    logger.log(Level.FINER, "ProcessCookies(): Found created timeStamp = " + createdTimeStampStr);

                    long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(createdTimeStampStr);
                    if (secsEpoch > 0) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START, aModuleName, secsEpoch));
                    }
                } else {
                    logger.log(Level.FINER, "ProcessCookies(): Failed to get created timeStamp.");
                }
            }

            // Get expires time
            NodeList expiresTimeNodes = cookieNode.getElementsByTagName(CellXMLDef.CLLXML_EXPIRES_TAG);
            if (expiresTimeNodes.getLength() > 0) {

                String expiresTimeStampStr = null;

                Element starttimeNode = (Element) createdTimeNodes.item(0);
                expiresTimeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, starttimeNode);
                if (null != expiresTimeStampStr) {
                    logger.log(Level.FINER, "ProcessCookies(): Found expires timeStamp = " + expiresTimeStampStr);

                    long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(expiresTimeStampStr);
                    if (secsEpoch > 0) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END, aModuleName, secsEpoch));
                    }
                } else {
                    logger.log(Level.FINER, "ProcessCookies(): Failed to get expires timeStamp.");
                }
            }

            try {
                // Create a Cookie Artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "ProcessCookies(): Failed to create blackboard artifact for Cookie. (" + ex.getLocalizedMessage() + ").");
            }

        } // for each cookie

    }

    private void ProcessInternetBookmarks(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList bookmarkNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_INTERNET_BOOKMARK_TAG);

        int len = bookmarkNodes.getLength();
        logger.log(Level.INFO, "ProcessInternetBookmarks(): Found  " + Integer.toString(len) + " Bookmark entries !!.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

            Element bookmarkNode = (Element) bookmarkNodes.item(c);

            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(bookmarkNode, attributes, aModuleName);

            // get name
            String urlName = null;
            urlName = GetXMLElemValue(CellXMLDef.CLLXML_URL_NAME_TAG, bookmarkNode);
            if (null != urlName) {
                logger.log(Level.FINER, "ProcessInternetBookmarks(): Found urlName = " + urlName);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, aModuleName, urlName));
            } else {
                logger.log(Level.FINER, "ProcessInternetBookmarks(): Failed to get urlName.");
            }

            // Get url address
            String urlAddress = null;
            urlAddress = GetXMLElemValue(CellXMLDef.CLLXML_URL_ADDRESS_TAG, bookmarkNode);
            if (null != urlAddress) {
                logger.log(Level.FINER, "ProcessInternetBookmarks(): Found urlAddress = " + urlAddress);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL, aModuleName, urlAddress));
            } else {
                logger.log(Level.FINER, "ProcessInternetBookmarks(): Failed to get urlAddress.");
            }

            // Get create time
            NodeList startTimeNodes = bookmarkNode.getElementsByTagName(CellXMLDef.CLLXML_TIME_TAG);
            if (startTimeNodes.getLength() > 0) {
                String createTimeStampStr = null;

                Element starttimeNode = (Element) startTimeNodes.item(0);
                createTimeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, starttimeNode);
                if (null != createTimeStampStr) {
                    logger.log(Level.FINER, "ProcessInternetBookmarks(): Found start timeStamp = " + createTimeStampStr);

                    long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(createTimeStampStr);
                    if (secsEpoch > 0) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED, aModuleName, secsEpoch));
                    }
                } else {
                    logger.log(Level.FINER, "ProcessInternetBookmarks(): Failed to get create timeStamp.");
                }
            }

            try {
                // Create a bookMark entry Artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "ProcessInternetBookmarks(): Failed to create blackboard artifact for Bookmark. (" + ex.getLocalizedMessage() + ").");
            }

        } // for each Bookmark entry

    }

    private void ProcessInternetHistory(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList historyNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_INTERNET_HISTORY_TAG);

        int len = historyNodes.getLength();
        logger.log(Level.INFO, "ProcessInternetHistory(): Found  " + Integer.toString(len) + " Internet History entries !!.");

        for (int c = 0; c < len; c++) {

            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

            Element historyNode = (Element) historyNodes.item(c);

            // Add a TSK_ISDELETED attribute if the artifact is deleted
            FlagDeletedContent(historyNode, attributes, aModuleName);

            // get name
            String urlName = null;
            urlName = GetXMLElemValue(CellXMLDef.CLLXML_URL_NAME_TAG, historyNode);
            if (null != urlName) {
                logger.log(Level.FINER, "ProcessInternetHistory(): Found urlName = " + urlName);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, aModuleName, urlName));
            } else {
                logger.log(Level.FINER, "ProcessInternetHistory(): Failed to get urlName.");
            }

            // Get url address
            String urlAddress = null;
            urlAddress = GetXMLElemValue(CellXMLDef.CLLXML_URL_ADDRESS_TAG, historyNode);
            if (null != urlAddress) {
                logger.log(Level.FINER, "ProcessInternetHistory(): Found urlAddress = " + urlAddress);
                attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL, aModuleName, urlAddress));
            } else {
                logger.log(Level.FINER, "ProcessInternetHistory(): Failed to get urlAddress.");
            }

            // Get access time
            NodeList startTimeNodes = historyNode.getElementsByTagName(CellXMLDef.CLLXML_TIME_TAG);
            if (startTimeNodes.getLength() > 0) {
                String accessedTimeStampStr = null;

                Element starttimeNode = (Element) startTimeNodes.item(0);
                accessedTimeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, starttimeNode);
                if (null != accessedTimeStampStr) {
                    logger.log(Level.FINER, "ProcessInternetHistory(): Found start timeStamp = " + accessedTimeStampStr);

                    long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(accessedTimeStampStr);
                    if (secsEpoch > 0) {
                        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, aModuleName, secsEpoch));
                    }
                } else {
                    logger.log(Level.FINER, "ProcessInternetHistory(): Failed to get access timeStamp.");
                }
            }

            try {
                // Create a Web history entry Artifact
                // Add the attributes, if there are any
                if (!attributes.isEmpty()) {
                    BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY);
                    bba.addAttributes(attributes);
                    indexArtifact(bba);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "ProcessInternetHistory(): Failed to create blackboard artifact for Internet History. (" + ex.getLocalizedMessage() + ").");
            }

        } // for each history entry

    }

    private void ProcessGPSFavorites(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList gpsFavNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_GPS_FAVORITES_TAG);

        if (gpsFavNodes.getLength() > 0) {

            Element gpsFavNode = (Element) gpsFavNodes.item(0);
            NodeList geoLocationNodes = gpsFavNode.getElementsByTagName(CellXMLDef.CLLXML_GEO_LOCATION_TAG);

            int len = geoLocationNodes.getLength();
            logger.log(Level.FINER, "ProcessGPSFavorites(): Found  " + Integer.toString(len) + " GPS favorites entries !!.");

            for (int c = 0; c < len; c++) {

                Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();
                Element geoLocationNode = (Element) geoLocationNodes.item(c);

                // Add a TSK_ISDELETED attribute if the artifact is deleted
                FlagDeletedContent(geoLocationNode, attributes, aModuleName);

                // get latitude
                String latitudeStr = null;
                latitudeStr = GetXMLElemValue(CellXMLDef.CLLXML_LATITUDE_TAG, geoLocationNode);
                if (null != latitudeStr) {
                    double latitude = Double.parseDouble(latitudeStr);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, aModuleName, latitude));
                } else {
                    logger.log(Level.FINER, "ProcessGPSFavorites(): Failed to get latitude.");
                }

                // get longitude
                String longitudeStr = null;
                longitudeStr = GetXMLElemValue(CellXMLDef.CLLXML_LONGITUDE_TAG, geoLocationNode);
                if (null != longitudeStr) {
                    double longitude = Double.parseDouble(longitudeStr);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, aModuleName, longitude));
                } else {
                    logger.log(Level.FINER, "ProcessGPSFavorites(): Failed to get longitude.");
                }

                // get altitude
                String altitudeStr = null;
                altitudeStr = GetXMLElemValue(CellXMLDef.CLLXML_ALTITUDE_TAG, geoLocationNode);
                if (null != altitudeStr) {
                    double altitude = Double.parseDouble(altitudeStr);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE, aModuleName, altitude));
                } else {
                    logger.log(Level.FINER, "ProcessGPSFavorites(): Failed to get longitude.");
                }

                // Get the name, if there is one
                String locationName = null;
                locationName = GetXMLElemValue(CellXMLDef.CLLXML_NAME_TAG, geoLocationNode);
                if (null != locationName) {
                    logger.log(Level.FINER, "ProcessGPSFavorites(): Found location name = " + locationName);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, aModuleName, locationName));
                } else {
                    logger.log(Level.FINER, "ProcessGPSFavorites(): Failed to get name.");
                }

                // Get the locartion address, if there is one
                String locationAddress = null;
                locationAddress = GetXMLElemValue(CellXMLDef.CLLXML_ADDRESS_TAG, geoLocationNode);
                if (null != locationAddress) {
                    logger.log(Level.FINER, "ProcessGPSFavorites(): Found location address = " + locationAddress);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION, aModuleName, locationAddress));
                } else {
                    logger.log(Level.FINER, "ProcessGPSFavorites(): Failed to get address.");
                }

                // Get  time - if there is any
                NodeList timeNodes = geoLocationNode.getElementsByTagName(CellXMLDef.CLLXML_TIME_TAG);
                if (timeNodes.getLength() > 0) {

                    String timeStampStr = null;

                    Element timeNode = (Element) timeNodes.item(0);
                    timeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, timeNode);
                    if (null != timeStampStr) {
                        logger.log(Level.FINER, "ProcessGPSFavorites(): Found created timeStamp = " + timeStampStr);

                        long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(timeStampStr);
                        if (secsEpoch > 0) {
                            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, aModuleName, secsEpoch));
                        }
                    } else {
                        logger.log(Level.FINER, "ProcessGPSFavorites(): Failed to get created timeStamp.");
                    }
                }

                try {
                    // Create a GPS Bookmark artifact
                    // Add the attributes, if there are any
                    if (!attributes.isEmpty()) {
                        BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK);
                        bba.addAttributes(attributes);
                        indexArtifact(bba);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "ProcessGPSFavorites(): Failed to create blackboard artifact for GPS Bookmark. (" + ex.getLocalizedMessage() + ").");
                }

            } // for each geoLocation

        }

    }

    private void ProcessGPSSearches(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList gpsSearchesNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_GPS_SEARCHES_TAG);

        if (gpsSearchesNodes.getLength() > 0) {

            Element gpsSearchesNode = (Element) gpsSearchesNodes.item(0);

            NodeList geoLocationNodes = gpsSearchesNode.getElementsByTagName(CellXMLDef.CLLXML_GEO_LOCATION_TAG);

            int len = geoLocationNodes.getLength();
            logger.log(Level.INFO, "ProcessGPSSearches(): Found  " + Integer.toString(len) + " GPS Search entries !!.");

            for (int c = 0; c < len; c++) {

                Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

                Element geoLocationNode = (Element) geoLocationNodes.item(c);

                // Add a TSK_ISDELETED attribute if the artifact is deleted
                FlagDeletedContent(geoLocationNode, attributes, aModuleName);

                // get latitude
                String latitudeStr = null;
                latitudeStr = GetXMLElemValue(CellXMLDef.CLLXML_LATITUDE_TAG, geoLocationNode);
                if (null != latitudeStr) {
                    double latitude = Double.parseDouble(latitudeStr);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, aModuleName, latitude));
                } else {
                    logger.log(Level.FINER, "ProcessGPSSearches(): Failed to get latitude.");
                }

                // get longitude
                String longitudeStr = null;
                longitudeStr = GetXMLElemValue(CellXMLDef.CLLXML_LONGITUDE_TAG, geoLocationNode);
                if (null != longitudeStr) {
                    double longitude = Double.parseDouble(longitudeStr);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, aModuleName, longitude));
                } else {
                    logger.log(Level.FINER, "ProcessGPSSearches(): Failed to get longitude.");
                }

                // get altitude
                String altitudeStr = null;
                altitudeStr = GetXMLElemValue(CellXMLDef.CLLXML_ALTITUDE_TAG, geoLocationNode);
                if (null != altitudeStr) {
                    double altitude = Double.parseDouble(altitudeStr);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE, aModuleName, altitude));
                } else {
                    logger.log(Level.FINER, "ProcessGPSSearches(): Failed to get longitude.");
                }

                // Get the name, if there is one
                String locationName = null;
                locationName = GetXMLElemValue(CellXMLDef.CLLXML_NAME_TAG, geoLocationNode);
                if (null != locationName) {
                    logger.log(Level.FINER, "ProcessGPSSearches(): Found location name = " + locationName);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, aModuleName, locationName));
                } else {
                    logger.log(Level.FINER, "ProcessGPSSearches(): Failed to get name.");
                }

                // Get the locartion address, if there is one
                String locationAddress = null;
                locationAddress = GetXMLElemValue(CellXMLDef.CLLXML_ADDRESS_TAG, geoLocationNode);
                if (null != locationAddress) {
                    logger.log(Level.FINER, "ProcessGPSSearches(): Found location address = " + locationAddress);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION, aModuleName, locationAddress));
                } else {
                    logger.log(Level.FINER, "ProcessGPSSearches(): Failed to get address.");
                }

                // Get  time - if there is any
                NodeList timeNodes = geoLocationNode.getElementsByTagName(CellXMLDef.CLLXML_TIME_TAG);
                if (timeNodes.getLength() > 0) {

                    String timeStampStr = null;

                    Element timeNode = (Element) timeNodes.item(0);
                    timeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, timeNode);
                    if (null != timeStampStr) {
                        logger.log(Level.FINER, "ProcessGPSFavorites(): Found created timeStamp = " + timeStampStr);

                        long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(timeStampStr);
                        if (secsEpoch > 0) {
                            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, aModuleName, secsEpoch));
                        }
                    } else {
                        logger.log(Level.FINER, "ProcessGPSFavorites(): Failed to get created timeStamp.");
                    }
                }

                try {
                    // Create a GPS Search artifact
                    // Add the attributes, if there are any
                    if (!attributes.isEmpty()) {
                        BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH);
                        bba.addAttributes(attributes);
                        indexArtifact(bba);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "ProcessGPSSearches(): Failed to create blackboard artifact for GPS Search. (" + ex.getLocalizedMessage() + ").");
                }

            } // for each geoLocation

        }

    }

    private void ProcessGPSLastKnownLocation(Document aCLLXMLDoc, AbstractContent abstractContent, String aModuleName) {

        Element rootElem = aCLLXMLDoc.getDocumentElement();
        NodeList gpsSearchesNodes = rootElem.getElementsByTagName(CellXMLDef.CLLXML_GPS_LAST_KNOWN_LOCATION_TAG);

        if (gpsSearchesNodes.getLength() > 0) {

            Element gpsSearchesNode = (Element) gpsSearchesNodes.item(0);

            NodeList geoLocationNodes = gpsSearchesNode.getElementsByTagName(CellXMLDef.CLLXML_GEO_LOCATION_TAG);

            int len = geoLocationNodes.getLength();
            logger.log(Level.INFO, "ProcessGPSLastKnownLocation(): Found  " + Integer.toString(len) + " GPS Last Known location !!.");

            for (int c = 0; c < len; c++) {

                Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();

                Element geoLocationNode = (Element) geoLocationNodes.item(c);

                // Add a TSK_ISDELETED attribute if the artifact is deleted
                FlagDeletedContent(geoLocationNode, attributes, aModuleName);

                // get latitude
                String latitudeStr = null;
                latitudeStr = GetXMLElemValue(CellXMLDef.CLLXML_LATITUDE_TAG, geoLocationNode);
                if (null != latitudeStr) {
                    double latitude = Double.parseDouble(latitudeStr);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, aModuleName, latitude));
                } else {
                    logger.log(Level.FINER, "ProcessGPSLastKnownLocation(): Failed to get latitude.");
                }

                // get longitude
                String longitudeStr = null;
                longitudeStr = GetXMLElemValue(CellXMLDef.CLLXML_LONGITUDE_TAG, geoLocationNode);
                if (null != longitudeStr) {
                    double longitude = Double.parseDouble(longitudeStr);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, aModuleName, longitude));
                } else {
                    logger.log(Level.FINER, "ProcessGPSLastKnownLocation(): Failed to get longitude.");
                }

                // get altitude
                String altitudeStr = null;
                altitudeStr = GetXMLElemValue(CellXMLDef.CLLXML_ALTITUDE_TAG, geoLocationNode);
                if (null != altitudeStr) {
                    double altitude = Double.parseDouble(altitudeStr);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE, aModuleName, altitude));
                } else {
                    logger.log(Level.FINER, "ProcessGPSLastKnownLocation(): Failed to get longitude.");
                }

                // Get the name, if there is one
                String locationName = null;
                locationName = GetXMLElemValue(CellXMLDef.CLLXML_NAME_TAG, geoLocationNode);
                if (null != locationName) {
                    logger.log(Level.FINER, "ProcessGPSLastKnownLocation(): Found location name = " + locationName);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, aModuleName, locationName));
                } else {
                    logger.log(Level.FINER, "ProcessGPSLastKnownLocation(): Failed to get name.");
                }

                // Get the locartion address, if there is one
                String locationAddress = null;
                locationAddress = GetXMLElemValue(CellXMLDef.CLLXML_ADDRESS_TAG, geoLocationNode);
                if (null != locationAddress) {
                    logger.log(Level.FINER, "ProcessGPSLastKnownLocation(): Found location address = " + locationAddress);
                    attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCATION, aModuleName, locationAddress));
                } else {
                    logger.log(Level.FINER, "ProcessGPSLastKnownLocation(): Failed to get address.");
                }

                // Get  time - if there is any
                NodeList timeNodes = geoLocationNode.getElementsByTagName(CellXMLDef.CLLXML_TIME_TAG);
                if (timeNodes.getLength() > 0) {

                    String timeStampStr = null;

                    Element timeNode = (Element) timeNodes.item(0);
                    timeStampStr = GetXMLElemValue(CellXMLDef.CLLXML_DATETIME_TAG, timeNode);
                    if (null != timeStampStr) {
                        logger.log(Level.FINER, "ProcessGPSLastKnownLocation(): Found created timeStamp = " + timeStampStr);

                        long secsEpoch = GetSecsSinceEpochFrom8601TimeStamp(timeStampStr);
                        if (secsEpoch > 0) {
                            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, aModuleName, secsEpoch));
                        }
                    } else {
                        logger.log(Level.FINER, "ProcessGPSLastKnownLocation(): Failed to get created timeStamp.");
                    }
                }

                try {
                    // Create a GPS Search artifact
                    // Add the attributes, if there are any
                    if (!attributes.isEmpty()) {
                        BlackboardArtifact bba = abstractContent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION);
                        bba.addAttributes(attributes);
                        indexArtifact(bba);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "ProcessGPSLastKnownLocation(): Failed to create blackboard artifact for GPS Last Location. (" + ex.getLocalizedMessage() + ").");
                }

            } // for each geoLocation

        }

    }

    /**
     * Index the text associated with the given artifact.
     *
     * @param artifact
     */
    private void indexArtifact(BlackboardArtifact artifact) throws TskCoreException {
        Blackboard blackboard = Case.getCurrentCase().getServices().getBlackboard();

        try {
            // index the artifact for keyword search
            blackboard.indexArtifact(artifact);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, NbBundle.getMessage(Blackboard.class, "Blackboard.unableToIndexArtifact.error.msg", artifact.getDisplayName()), ex); //NON-NLS
            MessageNotifyUtil.Notify.error(
                    NbBundle.getMessage(Blackboard.class, "Blackboard.unableToIndexArtifact.exception.msg"), artifact.getDisplayName());
        }
    }

}

/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.File;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AccountFileInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Account;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE;
import org.sleuthkit.datamodel.Relationship;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;


/**
 * 
 * A utility class to support modules that parser SQLite databases from mobile 
 * apps and create artifacts.
 */
public final class MobileAppDBParserHelper {
    
    // Store handle to temp copies of files
    private Map<Long, File> tempFiles = new HashMap<>();
    
    // Store artifacts created
    private Map<BlackboardArtifact.Type, List<BlackboardArtifact>> newArtifacts = new HashMap<>();
    
        
    // name of module that this is helping. 
    private final String moduleName; 
    
    
    static MobileAppDBParserHelper createInstance(String moduleName) {
        return new MobileAppDBParserHelper(moduleName);
    }
     
    MobileAppDBParserHelper(String moduleName) {
        this.moduleName = moduleName;
    }
    
    
    /**
     * Looks for the given SQLIte database filename, in the given path substring, 
     * and if found, returns an AbstractFile for it.
     * 
     * @param dataSource data source to search in 
     * @param dbNamePattern db file name pattern to search
     * @param pathSubStr path substring to match
     * 
     * @return AbstractFile for the DB if the database file is found.
     *         Returns NULL if no such database is found.
     */
    public AbstractFile findAppDB(DataSource dataSource, String dbNamePattern, String pathSubStr) {
        
        // RAMAN TBD
        // Find the DB file, ensure path matches
        // find the abstract file
        // Make a copy of DB in the temp folder and open a SQL connection to it 
        //  return abstract file
        
        return null;
    }
    
    /**
     * Checks if the specified table exists in the given database file.
     * 
     * @param dbFile database file
     * @param tableName table name to check
     * 
     * @return 
     */
    public boolean tableExists(AbstractFile dbFile, String tableName) {
        // RAMAN TBD
        return false;
        
    }
    
    /**
     * Checks if the specified column exists.
     * 
     * @param dbFile database file
     * @param tableName table name to check
     * @param columnName column name to check
     * @return 
     */
    public boolean columnExists(AbstractFile dbFile, String tableName, String columnName) {
        // RAMAN TBD
        return false;
    }
    
    
    /**
     * Makes a temp copy of the DB file, opens it as a database and runs the 
     * specified query on it.
     * 
     * @param dbFile database file
     * @param queryStr SQL string for the query to run
     * 
     * @return ResultSet from running the query. 
     *         
     */
    public ResultSet runQuery(AbstractFile dbFile, String queryStr) {
        // RAMAN TBD
        return null;
    }
    
    /**
     * Creates and adds a TSK_CONTACT artifact to the case, with specified 
     * attributes.
     * Also creates an account instance of specified type for the contact with the 
     * specified ID.
     * 
     * @param dbFile database file in which the contact was found
     * @param selfAccount device owners account, used to create relationships
     * @param contactAccountType type of account to create for the contact
     * @param contactAccountUniqueID unique id for the contact's account
     * @param contactName Name of contact
     * @param phoneNumber phone number for contact
     * @param emailAddr Email address for contact
     * @param otherAttributesList additional attributes for contact
     * 
     * @return artifact created
     * 
     */
    public BlackboardArtifact addContact(AbstractFile dbFile, Account selfAccount, Account.Type contactAccountType, String contactAccountUniqueID, 
                                            String contactName, String phoneNumber, String emailAddr, 
                                            Collection<BlackboardAttribute> otherAttributesList) {
        
        try {
            // RAMAN TBD
            
            // Create TSK_CONTACT artifact
            BlackboardArtifact contactArtifact = dbFile.newArtifact(TSK_CONTACT);
            
            // Add basic attributes for name phonenumber email, if specified
            ////    contactArtifact.bbart.addAttribute(new BlackboardAttribute(TSK_NAME, moduleName, contactName));
            ////    contactArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER, moduleName, phoneNumber));
            ////    contactArtifact.addAttribute(new BlackboardAttribute(TSK_EMAIL, moduleName, emailAddr));
            
            // Add other specified attributes
            ////    for (BlackboardAttribute otherAttribute: otherAttributesList) {
            ////        contactArtifact.addAttribute(otherAttribute)
            
            // Find/Create an account instance for the contact
            //      Account contactAccount = createAccountInstance(dbFile, contactAccountType, contactAccountUniqueID)
            
            // Create a relationship between selfAccount and contactAccount
            //      relationShip = addRelationship (selfAccount, contactAccount, contactArtifact, Relationship.Type.CONTACT, 0 )
            
            
            // index artifact
            ////    blackboard.indexArtifact(tifArtifact);
            
            // add artifact to newArtifacts map so it can be included in the ModuleDataEvent
            this.addToNewArtifacts(contactArtifact);
            
            // return the TSK_CONTACT artifact
            return contactArtifact;
        } catch (TskCoreException ex) {
            // TBD: log error suitably
            return null;
        }
    }
    
    
    /**
     * Gets/Creates an account file instance of the given type with the given ID.
     * 
     * @param dbFile file where the account instance is found
     * @param accountType account type
     * @param accountUniqueID unique identifier for account
     * 
     * @return account instance 
     * 
     * @throws TskCoreException 
     */
    public AccountFileInstance createAccountInstance(AbstractFile dbFile, Account.Type accountType, String accountUniqueID ) throws TskCoreException {
        
        
        return Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(accountType, accountUniqueID,  moduleName, dbFile);
    }
    
    
    /**
     * Adds a relations between the two specified account instances
     * 
     * @param selfAccount device owner account
     * @param otherAccount other account
     * @param sourceArtifact artifact from which relationship is derived.
     * @param relationshipType type of relationship
     * @param dateTime date/time of relationship 
     */
    private void addRelationship(AccountFileInstance selfAccount, AccountFileInstance otherAccount, BlackboardArtifact sourceArtifact, Relationship.Type relationshipType, long dateTime) {
        
        try {
            Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().addRelationships(selfAccount,
                    Collections.singletonList(otherAccount), sourceArtifact, relationshipType, dateTime);
        } catch (TskCoreException | TskDataException ex) {
            // Log error suitably
        }
        
        
    }
    
    
   
    /**
     * Adds a TSK_MESSAGE artifact. 
     * 
     * Also creates an account instance for the sender/receiver, and creates a 
     * relationship between the device owner account and the sender/receiver account.
     * 
     * 
     * @param dbFile
     * @param selfAccount
     * @param otherAccountType
     * @param otherAccountUniqueID
     * @param messageType
     * @param direction
     * @param fromAddress
     * @param toAddress
     * @param dateTime
     * @param readStatus
     * @param subject
     * @param messageText
     * @param threadId
     * @param otherAttributesList
     * 
     * 
     * @return 
     */
    public BlackboardArtifact addMessage(AbstractFile dbFile, Account selfAccount, Account.Type otherAccountType, String otherAccountUniqueID, 
                                            String messageType, String direction, String fromAddress, String toAddress, long dateTime, int readStatus, String subject, String messageText, String threadId,
                                            Collection<BlackboardAttribute> otherAttributesList) {
        
        
        try {
            // RAMAN TBD
            
            // Create TSK_MESSAGE artifact
            BlackboardArtifact msgArtifact = dbFile.newArtifact(TSK_MESSAGE);
            
            // Add basic attributes 
            ////    msgArtifact.bbart.addAttribute(new BlackboardAttribute(TSK_DIRECTION, moduleName, direction));
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER_FROM, moduleName, fromAddress));
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER_TO, moduleName, toAddress));
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_DATETIME, moduleName, dateTime));
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_READ_STATUS, moduleName, readStatus));
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_SUBJECT, moduleName, subject));
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_MESSAGE_TYPE, moduleName, messageText));
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_THREAD_ID, moduleName, threadId));
            
            
            // Add other specified attributes
            ////    for (BlackboardAttribute otherAttribute: otherAttributesList) {
            ////        contactArtifact.addAttribute(otherAttribute)
            
            // Find/Create an account instance for the contact
            //      Account contactAccount = createAccountInstance(dbFile, contactAccountType, contactAccountUniqueID)
            
            // Create a relationship between selfAccount and contactAccount
            //      relationShip = addRelationship (selfAccount, contactAccount, contactArtifact, Relationship.Type.MESSAGE, dateTime )
            
            
            // index artifact
            ////    blackboard.indexArtifact(msgArtifact);
            
            // add artifact to newArtifacts map so it can be included in the ModuleDataEvent
            addToNewArtifacts(msgArtifact);
            
            // return the message artifact
            return msgArtifact;
        } catch (TskCoreException ex) {
            // TBD: log error suitably
            return null;
        }
               
    }
    
    /**
     * Adds a TSK_CALLLOG artifact. 
     * 
     * Also creates an account instance for the caller/receiver, and creates a 
     * relationship between the device owner account and the caller/receiver account.
     * 
     * @param dbFile
     * @param selfAccount
     * @param otherAccountType
     * @param otherAccountUniqueID
     * @param direction
     * @param fromPhoneNumber
     * @param toPhoneNumber
     * @param startDateTime
     * @param endDateTime
     * @param contactName
     * @param otherAttributesList
     * 
     * @return 
     */
    public BlackboardArtifact addCalllog(AbstractFile dbFile, Account selfAccount, Account.Type otherAccountType, String otherAccountUniqueID, 
                                            String direction, String fromPhoneNumber, String toPhoneNumber, long startDateTime, long endDateTime, String contactName,
                                            Collection<BlackboardAttribute> otherAttributesList) {
        try {
            // RAMAN TBD
            
            // Create TSK_CALLLOG artifact
            BlackboardArtifact callLogArtifact = dbFile.newArtifact(TSK_CALLLOG);
            
            // Add basic attributes 
            ////    callLogArtifact.bbart.addAttribute(new BlackboardAttribute(TSK_DIRECTION, moduleName, direction));
            ////    callLogArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER_FROM, moduleName, fromPhoneNumber));
            ////    callLogArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER_TO, moduleName, toPhoneNumber));
            ////    callLogArtifact.addAttribute(new BlackboardAttribute(TSK_DATETIME_START, moduleName, startDateTime));
            ////    callLogArtifact.addAttribute(new BlackboardAttribute(TSK_DATETIME_END, moduleName, endDateTime));
            ////    callLogArtifact.addAttribute(new BlackboardAttribute(TSK_CONTACT_NAME, moduleName, contactName));
            
            
            // Add other specified attributes
            ////    for (BlackboardAttribute otherAttribute: otherAttributesList) {
            ////        callLogArtifact.addAttribute(otherAttribute)
            
            // Find/Create an account instance for the contact
            //      Account contactAccount = createAccountInstance(dbFile, otherAccountType, otherAccountUniqueID)
            
            // Create a relationship between selfAccount and contactAccount
            //      relationShip = addRelationship (selfAccount, contactAccount, contactArtifact, Relationship.Type.CALL_LOG, 0 )
            
            
            // index artifact
            ////    blackboard.indexArtifact(msgArtifact);
            
            // add artifact to newArtifacts map so it can be included in the ModuleDataEvent
            addToNewArtifacts(callLogArtifact);
            
            // return the callog artifact
            return callLogArtifact;
        } catch (TskCoreException ex) {
            // TBD: log error suitably
            return null;
        }
        
    }
    
    public void addAttachment() {
        
    }
    
    
    public BlackboardArtifact addWebBookmark() {
        return null;
    }
    
    public BlackboardArtifact addWebCookie() {
        return null;
    }
    
    public BlackboardArtifact addWebHistory() {
        return null;
    }
    
    public BlackboardArtifact addWebDownload() {
        return null;
    }
    
    public BlackboardArtifact addWebFormAutofill() {
        return null;
    }
    
    public BlackboardArtifact addWebFormAddress() {
        return null;
    }
    
    public BlackboardArtifact addWebCache() {
        return null;
    }
    
    public BlackboardArtifact addInstalledProgram() {
        return null;
    }
    
    public BlackboardArtifact addGPSTrackPoint() {
        return null;
    }
    
    
    

    
    /**
     * Performs cleanup and releases all resources
     * 
     */
    public void release() {
        
        // RAMAN TBD
        // for each artifact type in the newArtifacts, fire a ModuleDataEvent with all all the artifacts of that type
        
        // Close the DB connections to all files
        
        // delete all temp copies of DB files
    }
    
    private void addToNewArtifacts(BlackboardArtifact artifact) {
        
        // RAMAN TBD: add the artifact to the map, based on type.
    }
}

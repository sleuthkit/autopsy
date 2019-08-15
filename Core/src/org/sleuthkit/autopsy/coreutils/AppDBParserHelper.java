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

import java.util.Collection;
import java.util.Collections;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AccountFileInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Account;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Relationship;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;


/**
 * A helper class to support modules that parse SQLite databases from mobile 
 * apps and create artifacts.
 */
public final class AppDBParserHelper  {
    
    private static final Logger logger = Logger.getLogger(AppDBParserHelper.class.getName());
    
    private final AbstractFile dbAbstractFile;
    private final String moduleName;
    
    private final AccountFileInstance selfAccountInstance;
    
    // type of accounts to be created for this App DB
    private final Account.Type accountsType;
    
   
   
    private AppDBParserHelper(String moduleName, AbstractFile dbFile, AccountFileInstance selfAccountInstance, Account.Type accountsType ) {
        this.moduleName = moduleName;
        this.dbAbstractFile = dbFile;
        this.selfAccountInstance = selfAccountInstance;
        this.accountsType = accountsType;
    }
    
    /**
     * Constructs a AppDB parser helper for the given DB file.
     * 
     * This is a constructor for Apps that do not need to create any 
     * accounts/relationships.
     * 
     * @param moduleName name of module parsing the DB
     * @param dbFile db file
     * 
     */
    public AppDBParserHelper(String moduleName, AbstractFile dbFile) {
        this.moduleName = moduleName;
        this.dbAbstractFile = dbFile;
        this.selfAccountInstance = null;
        this.accountsType = null;
    }
    
    /**
     * Constructs a AppDB parser helper for the given DB file.
     * 
     * This is a constructor for Apps that that do not have any app specific account information 
     * for device owner and will use  a 'Device' account in lieu.
     * 
     * It creates a DeviceAccount instance to use as a self account.
     * 
     * @param moduleName
     * @param dbFile
     * @param accountsType
     * @param datasource
     * 
     * @throws TskCoreException 
     */
    public AppDBParserHelper(String moduleName, AbstractFile dbFile, Account.Type accountsType, DataSource datasource) throws TskCoreException {
        
        this.moduleName = moduleName;
        this.dbAbstractFile = dbFile;
        this.accountsType = accountsType;
        
        this.selfAccountInstance = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.DEVICE, datasource.getDeviceId(), moduleName, dbFile);
    }
    
    /**
     * Constructs a AppDB parser helper for the given DB file.
     * 
     * This constructor is for Apps that do have app specific account information 
     * for the device owner to create a 'self' account.
     * 
     * It creates a an account instance with specified type & id and uses it as
     * a self account.
     * 
     * @param moduleName
     * @param dbFile
     * @param accountsType
     * @param selfAccountType
     * @param selfAccountId
     * 
     * @throws TskCoreException 
     */
    public AppDBParserHelper(String moduleName, AbstractFile dbFile, Account.Type accountsType, Account.Type selfAccountType, String selfAccountId) throws TskCoreException {
        
        this.moduleName = moduleName;
        this.dbAbstractFile = dbFile;
        this.accountsType = accountsType;
        
        this.selfAccountInstance = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(selfAccountType, selfAccountId, moduleName, dbFile);
    }
    
  
    /**
     * Creates and adds a TSK_CONTACT artifact to the case, with specified 
     * attributes.
     * Also creates an account instance of specified type for the contact with the 
     * specified ID.
     * 
     * @param contactAccountUniqueID unique id for the contact's account
     * @param contactName Name of contact
     * @param phoneNumber primary phone number for contact
     * @param homePhoneNumber home phone number 
     * @param mobilePhoneNumber mobile phone number, 
     * @param emailAddr Email address for contact
     * 
     * 
     * @return artifact created
     * 
     */
    public BlackboardArtifact addContact(String contactAccountUniqueID, String contactName, 
                                         String phoneNumber, String homePhoneNumber, String mobilePhoneNumber, String emailAddr) {
        
        return addContact(contactAccountUniqueID, contactName,phoneNumber, homePhoneNumber,mobilePhoneNumber, emailAddr, Collections.EMPTY_LIST  );
        
    }
    
    
    /**
     * Creates and adds a TSK_CONTACT artifact to the case, with specified 
     * attributes.
     * Also creates an account instance of specified type for the contact with the 
     * specified ID.
     * 
     * @param contactAccountUniqueID unique id for the contact's account
     * @param contactName Name of contact
     * @param phoneNumber primary phone number for contact
     * @param homePhoneNumber home phone number 
     * @param mobilePhoneNumber mobile phone number, 
     * @param emailAddr Email address for contact
     * 
     * @param additionalAttributes additional attributes for contact
     * 
     * @return artifact created
     * 
     */
    public BlackboardArtifact addContact(String contactAccountUniqueID, String contactName, 
                                         String phoneNumber, String homePhoneNumber, String mobilePhoneNumber, String emailAddr,
                                         Collection<BlackboardAttribute> additionalAttributes) {
        
        try {
            // RAMAN TBD
            
            // Create TSK_CONTACT artifact
            BlackboardArtifact contactArtifact = this.dbAbstractFile.newArtifact(TSK_CONTACT);
            
            // Add basic attributes for name phonenumber email, if specified
            ////    contactArtifact.addAttribute(new BlackboardAttribute(TSK_NAME, moduleName, contactName));
            ////    if (!StringUtils.isEmpty(phoneNumber)) 
            ////          contactArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER, moduleName, phoneNumber));
            ////    if (!StringUtils.isEmpty(homeNumber)) 
            ////          contactArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER_HOME, moduleName, homeNumber));
            ////    if (!StringUtils.isEmpty(mobileNumber)) 
            ////          contactArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER_MOBILE, moduleName, mobileNumber));
            
            ////    if (!StringUtils.isEmpty(emailAddr)) 
            ////        contactArtifact.addAttribute(new BlackboardAttribute(TSK_EMAIL, moduleName, emailAddr));
            
            // Add additional specified attributes
            ////    for (BlackboardAttribute additionalAttribute: additionalAttributes) {
            ////        contactArtifact.addAttribute(additionalAttribute)
            
            // Find/Create an account instance for the contact
            //      Account contactAccount = createAccountInstance(dbFile, contactAccountType, contactAccountUniqueID)
            
            // Create a relationship between selfAccount and contactAccount
            //      relationShip = addRelationship (selfAccount, contactAccount, contactArtifact, Relationship.Type.CONTACT, 0 )
            
            
            // post artifact 
            ////     Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(contactArtifact);
            
            // return the TSK_CONTACT artifact
            return contactArtifact;
        } catch (TskCoreException ex) {
            // TBD: log error suitably
            return null;
        }
    }
    

    /**
     * Creates an account file instance associated with the DB file.
     * @param accountType
     * @param accountUniqueID
     * @return
     * @throws TskCoreException 
     */
    private  AccountFileInstance createAccountInstance(Account.Type accountType, String accountUniqueID ) throws TskCoreException {
        return Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(accountType, accountUniqueID,  moduleName, this.dbAbstractFile);
    }
    
    
    /**
     * Adds a relations between the two specified account instances.
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
     * 
     * @return message artifact 
     */
    public BlackboardArtifact addMessage(String otherAccountUniqueID, 
                                         String messageType, String direction, String fromAddress, String toAddress, 
                                         long dateTime, int readStatus, String subject, String messageText, String threadId) {
        
        return addMessage(otherAccountUniqueID, messageType,  direction,  fromAddress,  toAddress, 
                    dateTime,  readStatus,  subject,  messageText,  threadId, Collections.EMPTY_LIST);
    }
    
    /**
     * Adds a TSK_MESSAGE artifact. 
     * 
     * Also creates an account instance for the sender/receiver, and creates a 
     * relationship between the device owner account and the sender/receiver account.
     * 
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
     * 
     * @param otherAttributesList
     * 
     * 
     * @return 
     */
    public BlackboardArtifact addMessage(String otherAccountUniqueID, 
                                        String messageType, String direction, String fromAddress, String toAddress, long dateTime, int readStatus, String subject, String messageText, String threadId,
                                        Collection<BlackboardAttribute> otherAttributesList) {
        
        
        try {
            // RAMAN TBD
            
            // Create TSK_MESSAGE artifact
            BlackboardArtifact msgArtifact = dbAbstractFile.newArtifact(TSK_MESSAGE);
            
            // Add basic attribute, if the correspond value is specified
            ////    if (!StringUtils.isEmpty(direction)) 
            ////        msgArtifact.bbart.addAttribute(new BlackboardAttribute(TSK_DIRECTION, moduleName, direction));
            ////    if (!StringUtils.isEmpty(fromAddress)) 
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER_FROM, moduleName, fromAddress));
            ////    if (!StringUtils.isEmpty(toAddress)) 
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER_TO, moduleName, toAddress));
         
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_DATETIME, moduleName, dateTime));
            ////    msgArtifact.addAttribute(new BlackboardAttribute(TSK_READ_STATUS, moduleName, readStatus));
            
            ////    if (!StringUtils.isEmpty(subject)) 
            ////        msgArtifact.addAttribute(new BlackboardAttribute(TSK_SUBJECT, moduleName, subject));
            ////    if (!StringUtils.isEmpty(messageText)) 
            ////        msgArtifact.addAttribute(new BlackboardAttribute(TSK_MESSAGE_TYPE, moduleName, messageText));
            ////    if (!StringUtils.isEmpty(threadId)) 
            ////        msgArtifact.addAttribute(new BlackboardAttribute(TSK_THREAD_ID, moduleName, threadId));
            
            
            // Add other specified attributes
            ////    for (BlackboardAttribute otherAttribute: otherAttributesList) {
            ////        contactArtifact.addAttribute(otherAttribute)
            
            // Find/Create an account instance for the contact
            //      Account contactAccount = createAccountInstance(dbFile, contactAccountType, contactAccountUniqueID)
            
            // Create a relationship between selfAccount and contactAccount
            //      relationShip = addRelationship (selfAccount, contactAccount, msgArtifact, Relationship.Type.MESSAGE, dateTime )
            
            
            // post artifact
            ////    blackboard.postArtifact(msgArtifact);
            

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
     * @param selfAccount
     * @param otherAccountType
     * @param otherAccountUniqueID
     * @param direction
     * @param fromPhoneNumber
     * @param toPhoneNumber
     * @param startDateTime
     * @param endDateTime
     * @param contactName
     * 
     * @return 
     */
    public BlackboardArtifact addCalllog(Account selfAccount, Account.Type otherAccountType, String otherAccountUniqueID, 
                                            String direction, String fromPhoneNumber, String toPhoneNumber, long startDateTime, long endDateTime, String contactName) {
        
        return addCalllog(otherAccountUniqueID, direction, fromPhoneNumber, toPhoneNumber, startDateTime, endDateTime,  contactName, Collections.EMPTY_LIST );
    }
    
    /**
     * Adds a TSK_CALLLOG artifact. 
     * 
     * Also creates an account instance for the caller/receiver, and creates a 
     * relationship between the device owner account and the caller/receiver account.
     * 
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
    public BlackboardArtifact addCalllog(String otherAccountUniqueID, 
                                            String direction, String fromPhoneNumber, String toPhoneNumber, long startDateTime, long endDateTime, String contactName,
                                            Collection<BlackboardAttribute> otherAttributesList) {
        try {
            // RAMAN TBD
            
            // Create TSK_CALLLOG artifact
            BlackboardArtifact callLogArtifact = dbAbstractFile.newArtifact(TSK_CALLLOG);
            
            // Add basic attributes 
            ////    if (!StringUtils.isEmpty(direction)) 
            ////    callLogArtifact.bbart.addAttribute(new BlackboardAttribute(TSK_DIRECTION, moduleName, direction));
            ////    if (!StringUtils.isEmpty(fromPhoneNumber)) 
            ////    callLogArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER_FROM, moduleName, fromPhoneNumber));
            ////    if (!StringUtils.isEmpty(toPhoneNumber)) 
            ////    callLogArtifact.addAttribute(new BlackboardAttribute(TSK_PHONE_NUMBER_TO, moduleName, toPhoneNumber));
            ////    callLogArtifact.addAttribute(new BlackboardAttribute(TSK_DATETIME_START, moduleName, startDateTime));
            ////    callLogArtifact.addAttribute(new BlackboardAttribute(TSK_DATETIME_END, moduleName, endDateTime));
            ////    if (!StringUtils.isEmpty(contactName)) 
            ////    callLogArtifact.addAttribute(new BlackboardAttribute(TSK_CONTACT_NAME, moduleName, contactName));
            
            
            // Add other specified attributes
            ////    for (BlackboardAttribute otherAttribute: otherAttributesList) {
            ////        callLogArtifact.addAttribute(otherAttribute)
            
            // Find/Create an account instance for the contact
            //      Account contactAccount = createAccountInstance(dbFile, otherAccountType, otherAccountUniqueID)
            
            // Create a relationship between selfAccount and contactAccount
            //      relationShip = addRelationship (selfAccount, contactAccount, contactArtifact, Relationship.Type.CALL_LOG, 0 )
            
            
            // post artifact
            ////    blackboard.postArtifact(msgArtifact);
            
            // return the callog artifact
            return callLogArtifact;
        } catch (TskCoreException ex) {
            // TBD: log error suitably
            return null;
        }
        
    }
    
    // RAMAN TBD
    public void addAttachment(BlackboardArtifact parentArtifact) {
        // RAMAN TBD
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
    

}

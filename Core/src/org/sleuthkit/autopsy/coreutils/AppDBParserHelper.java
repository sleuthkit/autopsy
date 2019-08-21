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
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AccountFileInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard;
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
    
    // 'self' account for the application. 
    private final AccountFileInstance selfAccountInstance;
    
    // type of accounts to be created for the Application using this helper
    private final Account.Type accountsType;
    
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
     * 
     * @throws TskCoreException 
     */
    public AppDBParserHelper(String moduleName, AbstractFile dbFile, Account.Type accountsType) throws TskCoreException {
        
        this.moduleName = moduleName;
        this.dbAbstractFile = dbFile;
        this.accountsType = accountsType;
        this.selfAccountInstance = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.DEVICE, ((DataSource)dbFile.getDataSource()).getDeviceId(), moduleName, dbFile);
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
     * @return artifact created
     * 
     */
    public BlackboardArtifact addContact(String contactAccountUniqueID, String contactName, 
                                         String phoneNumber, String homePhoneNumber, 
                                         String mobilePhoneNumber, String emailAddr) {
        return addContact(contactAccountUniqueID, contactName,phoneNumber, 
                           homePhoneNumber,mobilePhoneNumber, emailAddr, 
                           Collections.<BlackboardAttribute>emptyList() );
    }
    
    
    /**
     * Creates and adds a TSK_CONTACT artifact to the case, with specified 
     * attributes.
     * Also creates an account instance for the contact with the 
     * specified ID.
     * 
     * @param contactAccountUniqueID unique id for contact account
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
                                         String phoneNumber, String homePhoneNumber, 
                                         String mobilePhoneNumber, String emailAddr,
                                         Collection<BlackboardAttribute> additionalAttributes) {
       
        BlackboardArtifact contactArtifact = null;
        try {
            // Create TSK_CONTACT artifact
            contactArtifact = this.dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_CONTACT);
            
            // Add basic attributes for name phonenumber email, if specified
            contactArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, contactName));
            
            if (!StringUtils.isEmpty(phoneNumber)) 
                  contactArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, moduleName, phoneNumber));
            if (!StringUtils.isEmpty(homePhoneNumber)) 
                  contactArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME, moduleName, homePhoneNumber));
            if (!StringUtils.isEmpty(mobilePhoneNumber)) 
                  contactArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE, moduleName, mobilePhoneNumber));
            if (!StringUtils.isEmpty(emailAddr)) 
                contactArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL, moduleName, emailAddr));
            
            // Add additional specified attributes
            for (BlackboardAttribute additionalAttribute: additionalAttributes) {
                contactArtifact.addAttribute(additionalAttribute);
            }
            
            // Find/Create an account instance for the contact
            // Create a relationship between selfAccount and contactAccount
            AccountFileInstance contactAccountInstance = createAccountInstance(accountsType, contactAccountUniqueID);
            if (selfAccountInstance != null) {
                addRelationship (selfAccountInstance, contactAccountInstance, contactArtifact, Relationship.Type.CONTACT, 0 );
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(contactArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add contact artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((contactArtifact != null)? contactArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        return contactArtifact;
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
    private void addRelationship(AccountFileInstance selfAccount, AccountFileInstance otherAccount, 
                                    BlackboardArtifact sourceArtifact, Relationship.Type relationshipType, long dateTime) {
        try {
            Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().addRelationships(selfAccount,
                    Collections.singletonList(otherAccount), sourceArtifact, relationshipType, dateTime);
        } catch (TskCoreException | TskDataException ex) {
            logger.log(Level.SEVERE, String.format("Unable to add relationship between account %s and account %s", selfAccount.toString(), otherAccount.toString()), ex); //NON-NLS
        }
    }
    
    
   /**
     * Adds a TSK_MESSAGE artifact. 
     * 
     * Also creates an account instance for the sender/receiver, and creates a 
     * relationship between the self account and the sender/receiver account.
     * 
     * @param otherAccountUniqueID unique id for the sender/receiver account
     * @param messageType message type
     * @param direction message direction
     * @param fromAddress sender address, may be empty
     * @param toAddress recipient address, may be empty
     * @param dateTime date/time of message, 
     * @param readStatus message read or not
     * @param subject message subject, may be empty
     * @param messageText message body, may be empty
     * @param threadId, message thread id
     * 
     * @return message artifact 
     */
    public BlackboardArtifact addMessage(String otherAccountUniqueID, 
                                         String messageType, String direction, String fromAddress, String toAddress, 
                                         long dateTime, int readStatus, String subject, String messageText, String threadId) {
        return addMessage(otherAccountUniqueID, messageType,  direction,  
                          fromAddress,  toAddress, dateTime,  readStatus,  
                          subject, messageText,  threadId, 
                          Collections.<BlackboardAttribute>emptyList());
    }
    
    /**
     * Adds a TSK_MESSAGE artifact. 
     * 
     * Also creates an account instance for the sender/receiver, and creates a 
     * relationship between the self account and the sender/receiver account.
     * 
     * @param otherAccountUniqueID unique id for the sender/receiver account
     * @param messageType message type
     * @param direction message direction
     * @param fromAddress sender address, may be empty
     * @param toAddress recipient address, may be empty
     * @param dateTime date/time of message, 
     * @param readStatus message read or not
     * @param subject message subject, may be empty
     * @param messageText message body, may be empty
     * @param threadId, message thread id
     * 
     * @param otherAttributesList additional attributes 
     * 
     * @return 
     */
    public BlackboardArtifact addMessage(String otherAccountUniqueID, 
                                        String messageType, String direction, String fromAddress, 
                                        String toAddress, long dateTime, int readStatus, String subject, 
                                        String messageText, String threadId,
                                        Collection<BlackboardAttribute> otherAttributesList) {
        
        BlackboardArtifact msgArtifact = null;
        try {
            // Create TSK_MESSAGE artifact
            msgArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_MESSAGE);
            msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, dateTime));
            msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_READ_STATUS, moduleName, readStatus));
            
            // Add basic attribute, if the correspond value is specified
            if (!StringUtils.isEmpty(messageType)) 
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, moduleName, messageType));
            if (!StringUtils.isEmpty(direction)) 
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName, direction));
            if (!StringUtils.isEmpty(fromAddress)) 
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, moduleName, fromAddress));
            if (!StringUtils.isEmpty(toAddress)) 
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, moduleName, toAddress));
         
            if (!StringUtils.isEmpty(subject)) 
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SUBJECT, moduleName, subject));
            if (!StringUtils.isEmpty(messageText)) 
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TEXT, moduleName, messageText));
            if (!StringUtils.isEmpty(threadId)) 
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_THREAD_ID, moduleName, threadId));
            
            
            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                msgArtifact.addAttribute(otherAttribute);
            }
            
            // Find/Create an account instance for the sender/recipient
            AccountFileInstance contactAccountInstance = createAccountInstance(accountsType, otherAccountUniqueID);
            
            // Create a relationship between selfAccount and contactAccount
            if (selfAccountInstance != null) {
                addRelationship (selfAccountInstance, contactAccountInstance, msgArtifact, Relationship.Type.MESSAGE, 0 );
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(msgArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add message artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((msgArtifact != null)? msgArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        // return the message artifact
        return msgArtifact;       
    }
    
    /**
     * Adds a TSK_CALLLOG artifact. 
     * 
     * Also creates an account instance for the caller/receiver, and creates a 
     * relationship between the self account and the caller/receiver account.
     * 
     * @param otherAccountUniqueID unique id for the caller/receiver account
     * @param direction call direction
     * @param fromPhoneNumber originating phone number, may be empty
     * @param toPhoneNumber recipient phone number, may be empty
     * @param startDateTime start date/time
     * @param endDateTime end date/time
     * @param contactName contact name, may be empty
     * 
     * @return 
     */
    public BlackboardArtifact addCalllog( String otherAccountUniqueID, 
                                          String direction, String fromPhoneNumber, String toPhoneNumber, 
                                          long startDateTime, long endDateTime, String contactName) {
        return addCalllog(otherAccountUniqueID, direction, fromPhoneNumber, toPhoneNumber, 
                            startDateTime, endDateTime,  contactName, 
                            Collections.<BlackboardAttribute>emptyList());
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
                                            String direction, String fromPhoneNumber, String toPhoneNumber, 
                                            long startDateTime, long endDateTime, String contactName,
                                            Collection<BlackboardAttribute> otherAttributesList) {
        BlackboardArtifact callLogArtifact = null;
        try {
            // Create TSK_CALLLOG artifact
            callLogArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_CALLLOG);
            
            // Add basic attributes 
            callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_START, moduleName, startDateTime));
            callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_END, moduleName, endDateTime));
            
            if (!StringUtils.isEmpty(direction)) 
                callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName, direction));
            if (!StringUtils.isEmpty(fromPhoneNumber)) 
                callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, moduleName, fromPhoneNumber));
            if (!StringUtils.isEmpty(toPhoneNumber)) 
                callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, moduleName, toPhoneNumber));
            if (!StringUtils.isEmpty(contactName)) 
                callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, contactName));
            
            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                callLogArtifact.addAttribute(otherAttribute);
            }
            
            // Find/Create an account instance for the sender/recipient
            // Create a relationship between selfAccount and contactAccount
            AccountFileInstance contactAccountInstance = createAccountInstance(accountsType, otherAccountUniqueID);
            if (selfAccountInstance != null) {
                addRelationship (selfAccountInstance, contactAccountInstance, callLogArtifact, Relationship.Type.CALL_LOG, 0 );
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(callLogArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add calllog artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((callLogArtifact != null)? callLogArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        // return the message artifact
        return callLogArtifact;      
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

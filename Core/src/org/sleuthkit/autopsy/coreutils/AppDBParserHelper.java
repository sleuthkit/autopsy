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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
    
    /**
     * Enum for message read status
     */
    public enum MessageReadStatusEnum {

        UNKNOWN,    /// read status is unknown
        UNREAD,     /// message has not been read
        READ        /// message has been read
    }
        
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
     * @param moduleName name module using the helper
     * @param dbFile database file being parsed by the module
     * @param accountsType account types created by this module
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
     * @param moduleName name module using the helper
     * @param dbFile database file being parsed by the module
     * @param accountsType account types created by this module
     * @param selfAccountType self account type to be created for this module
     * @param selfAccountId account unique id for the self account
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
     * @return contact artifact created
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
            
            if (!StringUtils.isEmpty(phoneNumber)) {
                  contactArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, moduleName, phoneNumber));
            }
            if (!StringUtils.isEmpty(homePhoneNumber)) {
                  contactArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME, moduleName, homePhoneNumber));
            }
            if (!StringUtils.isEmpty(mobilePhoneNumber)) {
                  contactArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE, moduleName, mobilePhoneNumber));
            }
            if (!StringUtils.isEmpty(emailAddr)) {
                contactArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL, moduleName, emailAddr));
            }
            
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
     * @param messageType message type
     * @param direction message direction
     * @param fromAddress sender address, may be null
     * @param toAddress recipient address, may be null
     * @param dateTime date/time of message, 
     * @param readStatus message read or not
     * @param subject message subject, may be empty
     * @param messageText message body, may be empty
     * @param threadId, message thread id
     * 
     * @return message artifact 
     */
    public BlackboardArtifact addMessage( 
                                String messageType, String direction, 
                                Account.Address fromAddress, 
                                Account.Address toAddress, 
                                long dateTime, MessageReadStatusEnum readStatus, 
                                String subject, String messageText, String threadId) {
        return addMessage(messageType,  direction,  
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
     * @return message artifact
     */
    public BlackboardArtifact addMessage( String messageType, String direction, 
                                Account.Address fromAddress, 
                                Account.Address toAddress, 
                                long dateTime, MessageReadStatusEnum readStatus, String subject, 
                                String messageText, String threadId,
                                Collection<BlackboardAttribute> otherAttributesList) {
        
        return addMessage(messageType, direction,
                fromAddress,
                Arrays.asList(toAddress),
                dateTime, readStatus,
                subject, messageText, threadId,
                otherAttributesList);
    }
    
     /**
     * Adds a TSK_MESSAGE artifact. 
     * 
     * Also creates an account instance for the sender/receiver, and creates a 
     * relationship between the self account and the sender/receiver account.
     * 
     * This method is for messages with a multiple recipients.
     * 
     * @param messageType message type
     * @param direction message direction
     * @param fromAddress sender address, may be null
     * @param recipientsList recipient address list, may be null or empty list
     * @param dateTime date/time of message, 
     * @param readStatus message read or not
     * @param subject message subject, may be empty
     * @param messageText message body, may be empty
     * @param threadId, message thread id
     * 
     * 
     * @return message artifact
     */
    public BlackboardArtifact addMessage( String messageType, String direction, 
                Account.Address fromAddress, 
                List<Account.Address> recipientsList, 
                long dateTime, MessageReadStatusEnum readStatus, 
                String subject, String messageText, String threadId) {
        return addMessage( messageType,  direction,  
                          fromAddress,  recipientsList, 
                          dateTime,  readStatus,  
                          subject, messageText,  threadId, 
                          Collections.<BlackboardAttribute>emptyList());
    }
    
    
    public BlackboardArtifact addMessage( String messageType, String direction, 
                Account.Address fromAddress, 
                List<Account.Address> recipientsList, 
                long dateTime, MessageReadStatusEnum readStatus, 
                String subject, String messageText, 
                String threadId, 
                Collection<BlackboardAttribute> otherAttributesList) {
       
         
        // Create a comma separated string of recipients
        String toAddresses =  null;
        if (recipientsList != null && (!recipientsList.isEmpty())) {
            StringBuilder toAddressesSb = new StringBuilder();
            for(Account.Address recipient : recipientsList) {
                toAddressesSb = toAddressesSb.length() > 0 ? toAddressesSb.append(",").append(recipient.getDisplayName()) : toAddressesSb.append(recipient.getDisplayName());
            }
            toAddresses =  toAddressesSb.toString();
        }
    
        // Created message artifact.  
        BlackboardArtifact msgArtifact = null;
        try {
            // Create TSK_MESSAGE artifact
            msgArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_MESSAGE);
            if (dateTime > 0) {
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, dateTime));
            }
            if (readStatus != MessageReadStatusEnum.UNKNOWN) {
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_READ_STATUS, moduleName, (readStatus == MessageReadStatusEnum.READ) ? 1 : 0));
            }

            // Add basic attribute, if the correspond value is specified
            if (!StringUtils.isEmpty(messageType)) {
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, moduleName, messageType));
            }
            if (!StringUtils.isEmpty(direction)) {
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName, direction));
            }
            if (fromAddress != null && !StringUtils.isEmpty(fromAddress.getDisplayName())) {
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, moduleName, fromAddress.getDisplayName()));
            }
            if (toAddresses != null && !StringUtils.isEmpty(toAddresses)) {
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, moduleName, toAddresses));
            }

            if (!StringUtils.isEmpty(subject)) {
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SUBJECT, moduleName, subject));
            }
            if (!StringUtils.isEmpty(messageText)) {
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TEXT, moduleName, messageText));
            }
            if (!StringUtils.isEmpty(threadId)) {
                msgArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_THREAD_ID, moduleName, threadId));
            }


            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                msgArtifact.addAttribute(otherAttribute);
            }

            // Find/create an account instance for sender
            if (fromAddress != null) {
                AccountFileInstance senderAccountInstance = createAccountInstance(accountsType, fromAddress.getUniqueID());

               // Create a relationship between selfAccount and sender account
               if (selfAccountInstance != null) {
                   addRelationship (selfAccountInstance, senderAccountInstance, msgArtifact, Relationship.Type.MESSAGE, dateTime );
               }
            }

            // Find/create an account instance for each recipient  
            if (recipientsList != null) {
                for(Account.Address recipient : recipientsList) {

                   AccountFileInstance recipientAccountInstance = createAccountInstance(accountsType, recipient.getUniqueID());

                   // Create a relationship between selfAccount and recipient account
                   if (selfAccountInstance != null) {
                       addRelationship (selfAccountInstance, recipientAccountInstance, msgArtifact, Relationship.Type.MESSAGE, dateTime );
                   }
                }
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
        
        // return the artifact
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
     * @return call log artifact
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
     * @return calllog artifact
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
            if (startDateTime > 0) {
                callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_START, moduleName, startDateTime));
            }
            if (endDateTime > 0) {
                callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_END, moduleName, endDateTime));
            }
            
            if (!StringUtils.isEmpty(direction)) {
                callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName, direction));
            }
            if (!StringUtils.isEmpty(fromPhoneNumber)) {
                callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, moduleName, fromPhoneNumber));
            }
            if (!StringUtils.isEmpty(toPhoneNumber)) {
                callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, moduleName, toPhoneNumber));
            }
            if (!StringUtils.isEmpty(contactName)) {
                callLogArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, contactName));
            }
            
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
        
        // return the artifact
        return callLogArtifact;      
    }
    
   
    /**
     *  Adds a TSK_WEB_BOOKMARK artifact. 
     * 
     * @param url bookmark URL 
     * @param title bookmark title, may be empty
     * @param creationTime date/time created
     * @param progName application/program that created bookmark
     * 
     * @return bookmark artifact
     */
    public BlackboardArtifact addWebBookmark(String url, String title, long creationTime, String progName) {
          return addWebBookmark(url, title,  creationTime, progName,
                            Collections.<BlackboardAttribute>emptyList());
    }
    
    /**
     *  Adds a TSK_WEB_BOOKMARK artifact. 
     * 
     * @param url bookmark URL 
     * @param title bookmark title, may be empty
     * @param creationTime date/time created
     * @param progName application/program that created bookmark
     * @param otherAttributesList other attributes

     * @return bookmark artifact
     */
    public BlackboardArtifact addWebBookmark(String url, String title, long creationTime, String progName,
             Collection<BlackboardAttribute> otherAttributesList) {
        
        BlackboardArtifact bookMarkArtifact = null;
        try {
            // Create artifact
            bookMarkArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
            
            // Add basic attributes 
            bookMarkArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL, moduleName, url));
            if (creationTime > 0) {
                bookMarkArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED, moduleName, creationTime));
            }
            
            if (!StringUtils.isEmpty(title)) {
                bookMarkArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE, moduleName, title));
            }
            if (!StringUtils.isEmpty(url)) {
                bookMarkArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, moduleName, NetworkUtils.extractDomain(url)));
            }
            if (!StringUtils.isEmpty(progName)) {
                bookMarkArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, moduleName, progName));
            }
            
            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                bookMarkArtifact.addAttribute(otherAttribute);
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(bookMarkArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add bookmark artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((bookMarkArtifact != null)? bookMarkArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        // return the artifact
        return bookMarkArtifact;  
    }
    
    
    /**
     * Adds a TSK_WEB_COOKIE artifact
     * 
     * @param url url of the site that created the cookie
     * @param creationTime create time of cookie
     * @param name cookie name 
     * @param value cookie value
     * @param programName name of the application that created the cookie
     * 
     * @return WebCookie artifact
     */
     public BlackboardArtifact addWebCookie(String url, long creationTime, 
             String name, String value, String programName) {
        
        return addWebCookie(url, creationTime, name, value, programName,
                 Collections.<BlackboardAttribute>emptyList());
    }
     
    /**
     * Adds a TSK_WEB_COOKIE artifact
     * 
     * @param url url of the site that created the cookie
     * @param creationTime create time of cookie
     * @param name cookie name 
     * @param value cookie value
     * @param programName name of the application that created the cookie
     * 
     * @param otherAttributesList other attributes
     * 
     * @return WebCookie artifact
     */
    public BlackboardArtifact addWebCookie(String url,
            long creationTime, String name, String value, String programName,
            Collection<BlackboardAttribute> otherAttributesList) {
        
        
        BlackboardArtifact cookieArtifact = null;
        try {
            // Create artifact
            cookieArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE);
            
            // Add basic attributes 
            cookieArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL, moduleName, url));
            if (creationTime > 0) {
                cookieArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, creationTime));
            }
            
            if (!StringUtils.isEmpty(name)) {
                cookieArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, name));
            }
            if (!StringUtils.isEmpty(value)) {
                cookieArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE, moduleName, value));
            }
            if (!StringUtils.isEmpty(url)) {
                cookieArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, moduleName, NetworkUtils.extractDomain(url)));
            }
            if (!StringUtils.isEmpty(programName)) {
                cookieArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, moduleName, programName));
            }
            
            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                cookieArtifact.addAttribute(otherAttribute);
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(cookieArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add bookmark artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((cookieArtifact != null)? cookieArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        // return the artifact
        return cookieArtifact;  
    }
    
    /**
     * Adds a Web History artifact
     * 
     * @param url url visited
     * @param accessTime last access time
     * @param referrer referrer, may be empty
     * @param title website title, may be empty
     * @param programName, application recording the history
     * 
     * @return artifact created
     */
    public BlackboardArtifact addWebHistory(String url, long accessTime, 
            String referrer, String title, String programName) {
        return addWebHistory(url, accessTime, referrer, title, programName,
                Collections.<BlackboardAttribute>emptyList());
    }
    
    /**
     * Adds a Web History artifact
     * 
     * @param url url visited
     * @param accessTime last access time
     * @param referrer referrer, may be empty
     * @param title website title, may be empty
     * @param programName, application recording the history
     * @param otherAttributesList other attributes
     * 
     * 
     * 
     * @return artifact created
     */
    public BlackboardArtifact addWebHistory(String url, long accessTime, 
            String referrer, String title, String programName, 
            Collection<BlackboardAttribute> otherAttributesList) {
        
        BlackboardArtifact webHistoryArtifact = null;
        try {
            // Create artifact
            webHistoryArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
            
            // Add basic attributes 
            webHistoryArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL, moduleName, url));
            if (accessTime > 0) {
                webHistoryArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, moduleName, accessTime));
            }
            
            if (!StringUtils.isEmpty(title)) {
                webHistoryArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE, moduleName, title));
            }
            if (!StringUtils.isEmpty(referrer)) {
                webHistoryArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER, moduleName, referrer));
            }
   
            if (!StringUtils.isEmpty(programName)) {
                webHistoryArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, moduleName, programName));
            }
             if (!StringUtils.isEmpty(url)) {
                webHistoryArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, moduleName, NetworkUtils.extractDomain(url)));
            }
            
            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                webHistoryArtifact.addAttribute(otherAttribute);
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(webHistoryArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add bookmark artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((webHistoryArtifact != null)? webHistoryArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        // return the artifact
        return webHistoryArtifact;  
    }
    
    /**
     * Created a TSK_WEB_DOWNNLOAD artifact
     * 
     * @param path path of downloaded file
     * @param startTime date/time downloaded
     * @param url URL downloaded from
     * @param progName program that initiated download
     * 
     * @return artifact created
     */
    public BlackboardArtifact addWebDownload(String path, long startTime, String url, String progName) {
        return addWebDownload(path, startTime, url, progName, Collections.<BlackboardAttribute>emptyList() );
    }
    
    /**
     * Created a TSK_WEB_DOWNNLOAD artifact
     * 
     * @param path path of downloaded file
     * @param startTime date/time downloaded
     * @param url URL downloaded from
     * @param programName program that initiated download
     * @param otherAttributesList other attributes
     * 
     * 
     * @return artifact created
     */
     public BlackboardArtifact addWebDownload(String path, long startTime, String url, String programName, 
             Collection<BlackboardAttribute> otherAttributesList ) {
         
        BlackboardArtifact webDownloadArtifact = null;
        try {
            // Create artifact
            webDownloadArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD);
            
            // Add basic attributes 
            webDownloadArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL, moduleName, url));
            if (startTime > 0) {
                webDownloadArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, moduleName, startTime));
            }
            webDownloadArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH, moduleName, path));
            
            /** Convert path to pathID ****/
//            long pathID = Util.findID(dataSource, downloadedFilePath);
//            if (pathID != -1) {
//                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID, moduleName, pathID));
//            }
                        
            if (!StringUtils.isEmpty(programName)) {
                webDownloadArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, moduleName, programName));
            }
            if (!StringUtils.isEmpty(url)) {
                webDownloadArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, moduleName, NetworkUtils.extractDomain(url)));
            }
            
            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                webDownloadArtifact.addAttribute(otherAttribute);
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(webDownloadArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add web download artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((webDownloadArtifact != null)? webDownloadArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        // return the artifact
        return webDownloadArtifact;  
    }
    
     
   /**
    * Adds a TSK_WEB_FORM_AUTOFILL artifact
    * 
    * @param name name of autofill field
    * @param value value of autofill field
    * @param creationTime create date/time
    * @param accessTime last access date/time
    * @param count count of times used
    * 
    * @return artifact created
    */
    public BlackboardArtifact addWebFormAutofill(String name, String value, 
            long creationTime, long accessTime, int count) {
        return addWebFormAutofill(name, value, creationTime, accessTime, count, 
                    Collections.<BlackboardAttribute>emptyList() );
    }
    
    /**
    * Adds a TSK_WEB_FORM_AUTOFILL artifact
    * 
    * @param name name of autofill field
    * @param value value of autofill field
    * @param creationTime create date/time
    * @param accessTime last access date/time
    * @param count count of times used
    * @param otherAttributesList additional attributes
    * 
    * @return artifact created
    */
    public BlackboardArtifact addWebFormAutofill(String name, String value, 
            long creationTime, long accessTime, int count,  
            Collection<BlackboardAttribute> otherAttributesList ) {
        BlackboardArtifact webFormAutofillArtifact = null;
        try {
            // Create artifact
            webFormAutofillArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_FORM_AUTOFILL);
            
            // Add basic attributes 
            webFormAutofillArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, name));
            webFormAutofillArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE, moduleName, value));
            if (creationTime > 0) {
                webFormAutofillArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED, moduleName, creationTime));
            }
            if (accessTime > 0) {
                webFormAutofillArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, moduleName, accessTime));
            }
            if (count > 0) {
                webFormAutofillArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT, moduleName, count));
            }
                            
            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                webFormAutofillArtifact.addAttribute(otherAttribute);
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(webFormAutofillArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add web form autofill artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((webFormAutofillArtifact != null)? webFormAutofillArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        // return the artifact
        return webFormAutofillArtifact;  
    }
     
    
    /**
     * Adds a TSK_WEB_FORM_AUTOFILL artifact.
     * 
     * @param personName person name
     * @param email email address
     * @param phoneNumber phone number
     * @param mailingAddress mailing address
     * @param creationTime creation time
     * @param accessTime last access time
     * @param count use count
     * 
     * @return artifact created
     */
    public BlackboardArtifact addWebFormAddress(String personName, String email, 
                                String phoneNumber, String mailingAddress, 
                                long creationTime, long accessTime, int count ) {
       return addWebFormAddress(personName, email, phoneNumber, 
                    mailingAddress, creationTime, accessTime, count, 
                    Collections.<BlackboardAttribute>emptyList() );
    }
    
    /**
     * Adds a TSK_WEB_FORM_AUTOFILL artifact.
     * 
     * @param personName person name
     * @param email email address
     * @param phoneNumber phone number
     * @param mailingAddress mailing address
     * @param creationTime creation time
     * @param accessTime last access time
     * @param count use count
     * @param otherAttributesList other attributes
     * 
     * @return artifact created
     */
    public BlackboardArtifact addWebFormAddress(String personName, String email, 
            String phoneNumber, String mailingAddress, 
            long creationTime, long accessTime, int count,
            Collection<BlackboardAttribute> otherAttributesList ) {
        
        BlackboardArtifact webFormAddressArtifact = null;
        try {
            // Create artifact
            webFormAddressArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_FORM_AUTOFILL);
            
            // Add basic attributes 
            webFormAddressArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, personName));
            if (creationTime > 0) {
                webFormAddressArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED, moduleName, creationTime));
            }
            if (accessTime > 0) {
                webFormAddressArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED, moduleName, accessTime));
            }
            if (count > 0) {
                webFormAddressArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT, moduleName, count));
            }
            
            if (!StringUtils.isEmpty(email)) {
                webFormAddressArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EMAIL, moduleName, email));
            }
            if (!StringUtils.isEmpty(phoneNumber)) {
                webFormAddressArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, moduleName, phoneNumber));
            }
            if (!StringUtils.isEmpty(mailingAddress)) {
                webFormAddressArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LOCATION, moduleName, mailingAddress));
            }
                            
            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                webFormAddressArtifact.addAttribute(otherAttribute);
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(webFormAddressArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add web form address artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((webFormAddressArtifact != null)? webFormAddressArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        // return the artifact
        return webFormAddressArtifact;
    }
    
    /**
     * Adds a TSK_INSTALLED_PROGRAM artifact
     * 
     * @param programName name of program 
     * @param dateInstalled date of install
     * 
     * @return artifact added
     */
    public BlackboardArtifact addInstalledProgram(String programName, long dateInstalled) {
        return addInstalledProgram(programName, dateInstalled,
                Collections.<BlackboardAttribute>emptyList() );
    }
    
    /**
     * Adds a TSK_INSTALLED_PROGRAM artifact
     * 
     * @param programName name of program 
     * @param dateInstalled date of install
     * @param otherAttributesList additional attributes
     * 
     * @return artifact added
     */
    public BlackboardArtifact addInstalledProgram(String programName, long dateInstalled, 
            Collection<BlackboardAttribute> otherAttributesList ) {
        
        BlackboardArtifact installedProgramArtifact = null;
        try {
            // Create artifact
            installedProgramArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_INSTALLED_PROG);
            
            // Add basic attributes 
            installedProgramArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, moduleName, programName));
            if (dateInstalled > 0) {
                installedProgramArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, dateInstalled));
            }
                            
            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                installedProgramArtifact.addAttribute(otherAttribute);
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(installedProgramArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add installed program artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((installedProgramArtifact != null)? installedProgramArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        // return the artifact
        return installedProgramArtifact;
    }
    
    
    /**
     * Adds a TSK_GPS_TRACKPOINT artifact
     * 
     * @param latitude location latitude
     * @param longitude location longitude
     * @param timeStamp date/time trackpoint recoded
     * @param poiName trackpoint name
     * @param programName name of program that recorded trackpoint
     * 
     * @return artifact added
     */
    public BlackboardArtifact addGPSLocation(double latitude, double longitude, 
            long timeStamp, String poiName, String programName) {
        
        return addGPSLocation(latitude, longitude, timeStamp, poiName, programName,
                Collections.<BlackboardAttribute>emptyList());
    }
    
    /**
     * Adds a TSK_GPS_TRACKPOINT artifact
     * 
     * @param latitude location latitude
     * @param longitude location longitude
     * @param timeStamp date/time trackpoint recorded
     * @param name trackpoint name
     * @param programName name of program that recorded trackpoint 
     * @param otherAttributesList other attributes
     * 
     * @return artifact added
     */
    public BlackboardArtifact addGPSLocation(double latitude, double longitude, long timeStamp, String name, String programName, 
            Collection<BlackboardAttribute> otherAttributesList) {
       
        BlackboardArtifact gpsTrackpointArtifact = null;
        try {
            // Create artifact
            gpsTrackpointArtifact = dbAbstractFile.newArtifact(ARTIFACT_TYPE.TSK_GPS_TRACKPOINT);
            
            // Add basic attributes 
            gpsTrackpointArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE, moduleName, latitude));
            gpsTrackpointArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE, moduleName, longitude));
            if (timeStamp > 0) {
                gpsTrackpointArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, timeStamp));
            }
            
            if (!StringUtils.isEmpty(name)) {
                gpsTrackpointArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME, moduleName, name));
            }
            
            if (!StringUtils.isEmpty(programName)) {
                gpsTrackpointArtifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME, moduleName, programName));
            }
                            
            // Add other specified attributes
            for (BlackboardAttribute otherAttribute: otherAttributesList) {
                gpsTrackpointArtifact.addAttribute(otherAttribute);
            }
            
            // post artifact 
            Case.getCurrentCase().getSleuthkitCase().getBlackboard().postArtifact(gpsTrackpointArtifact, this.moduleName);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to add GPS trackpoint artifact", ex); //NON-NLS
            return null;
        } 
        catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, String.format("Unable to post artifact %s", ((gpsTrackpointArtifact != null)? gpsTrackpointArtifact.getArtifactID() : "")), ex);  //NON-NLS
        }
        
        // return the artifact
        return gpsTrackpointArtifact;              
    }
    
}

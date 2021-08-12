/*
 * Central Repository
 *
 * Copyright 2015-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * Used to store details about a specific instance of a CorrelationAttribute.
 * Includes its data source, path, etc.
 *
 */
@Messages({
    "EamArtifactInstances.knownStatus.bad=Bad",
    "EamArtifactInstances.knownStatus.known=Known",
    "EamArtifactInstances.knownStatus.unknown=Unknown"})
public class CorrelationAttributeInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    private int ID;
    private String correlationValue;
    private CorrelationAttributeInstance.Type correlationType;
    private CorrelationCase correlationCase;
    private CorrelationDataSource correlationDataSource;
    private String filePath;
    private String comment;
    private TskData.FileKnown knownStatus;
    private Long objectId;
    private Long accountId;

    public CorrelationAttributeInstance(
            CorrelationAttributeInstance.Type correlationType,
            String correlationValue,
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource,
            String filePath,
            String comment,
            TskData.FileKnown knownStatus,
            long fileObjectId) throws CentralRepoException, CorrelationAttributeNormalizationException {
        this(correlationType, correlationValue, -1, eamCase, eamDataSource, filePath, comment, knownStatus, fileObjectId);
    }

    CorrelationAttributeInstance(
            Type type,
            String value,
            int instanceId,
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource,
            String filePath,
            String comment,
            TskData.FileKnown knownStatus,
            Long fileObjectId
    ) throws CentralRepoException, CorrelationAttributeNormalizationException {
         this(type, value, -1, eamCase, eamDataSource, filePath, comment, knownStatus, fileObjectId, (long)-1);
    }
    public CorrelationAttributeInstance(
            Type type,
            String value,
            int instanceId,
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource,
            String filePath,
            String comment,
            TskData.FileKnown knownStatus,
            Long fileObjectId,
            Long accountId
    ) throws CentralRepoException, CorrelationAttributeNormalizationException {
        if (filePath == null) {
            throw new CentralRepoException("file path is null");
        }

        this.correlationType = type;
        this.correlationValue = CorrelationAttributeNormalizer.normalize(type, value);
        this.ID = instanceId;
        this.correlationCase = eamCase;
        this.correlationDataSource = eamDataSource;
        // Lower case paths to normalize paths and improve correlation results, if this causes significant issues on case-sensitive file systems, remove
        this.filePath = filePath.toLowerCase();
        this.comment = comment;
        this.knownStatus = knownStatus;
        this.objectId = fileObjectId;
        this.accountId = accountId;
    }

    public Boolean equals(CorrelationAttributeInstance otherInstance) {
        return ((this.getID() == otherInstance.getID())
                && (this.getCorrelationValue().equals(otherInstance.getCorrelationValue()))
                && (this.getCorrelationType().equals(otherInstance.getCorrelationType()))
                && (this.getCorrelationCase().equals(otherInstance.getCorrelationCase()))
                && (this.getCorrelationDataSource().equals(otherInstance.getCorrelationDataSource()))
                && (this.getFilePath().equals(otherInstance.getFilePath()))
                && (this.getKnownStatus().equals(otherInstance.getKnownStatus()))
                && (this.getComment().equals(otherInstance.getComment()))
                && (this.getAccountId().equals(otherInstance.getAccountId())));
    }

    @Override
    public String toString() {
        return this.getID()
                + this.getCorrelationCase().getCaseUUID()
                + this.getCorrelationDataSource().getDeviceID()
                + this.getAccountId()
                + this.getFilePath()
                + this.getCorrelationType().toString()
                + this.getCorrelationValue()
                + this.getKnownStatus()
                + this.getComment();
    }

    /**
     * @return the correlationValue
     */
    public String getCorrelationValue() {
        return correlationValue;
    }

    /**
     * @return the correlation Type
     */
    public Type getCorrelationType() {
        return correlationType;
    }

    /**
     * Is this a database instance?
     *
     * @return True if the instance ID is greater or equal to zero; otherwise
     *         false.
     */
    public boolean isDatabaseInstance() {
        return (ID >= 0);
    }

    /**
     * @return the database ID
     */
    public int getID() {
        return ID;
    }

    /**
     * @return the eamCase
     */
    public CorrelationCase getCorrelationCase() {
        return correlationCase;
    }

    /**
     * @return the eamDataSource
     */
    public CorrelationDataSource getCorrelationDataSource() {
        return correlationDataSource;
    }

    /**
     * @return the filePath
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * @return the comment
     */
    public String getComment() {
        return null == comment ? "" : comment;
    }

    /**
     * @param comment the comment to set
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Get this knownStatus. This only indicates whether an item has been tagged
     * as notable and should never return KNOWN.
     *
     * @return BAD if the item has been tagged as notable, UNKNOWN otherwise
     */
    public TskData.FileKnown getKnownStatus() {
        return knownStatus;
    }

    /**
     * Set the knownStatus. This only indicates whether an item has been tagged
     * as notable and should never be set to KNOWN.
     *
     * @param knownStatus Should be BAD if the item is tagged as notable,
     *                    UNKNOWN otherwise
     */
    public void setKnownStatus(TskData.FileKnown knownStatus) {
        this.knownStatus = knownStatus;
    }

    /**
     * Get the objectId of the file associated with the correlation attribute or
     * NULL if the objectId is not available.
     *
     * @return the objectId of the file
     */
    public Long getFileObjectId() {
        return objectId;
    }

    /**
     * Get the accountId of the account associated with the correlation
     * attribute.
     *
     * @return the accountId of the account
     */
    public Long getAccountId() {
        return accountId;
    }

    /**
     * Set the accountId of the account associated with this correlation
     * attribute.
     */
    void setAccountId(Long accountId) {
        this.accountId = accountId;
    }
    
    // Type ID's for Default Correlation Types
    public static final int FILES_TYPE_ID = 0;
    public static final int DOMAIN_TYPE_ID = 1;
    public static final int EMAIL_TYPE_ID = 2;
    public static final int PHONE_TYPE_ID = 3;
    public static final int USBID_TYPE_ID = 4;
    public static final int SSID_TYPE_ID = 5;
    public static final int MAC_TYPE_ID = 6;
    public static final int IMEI_TYPE_ID = 7;
    public static final int IMSI_TYPE_ID = 8;
    public static final int ICCID_TYPE_ID = 9;
    public static final int INSTALLED_PROGS_TYPE_ID = 10;
    public static final int OSACCOUNT_TYPE_ID = 11;
    
    // An offset to assign Ids for additional  correlation types.
    public static final int ADDITIONAL_TYPES_BASE_ID = 1000;

    /**
     * Load the default correlation types
     *
     * @throws CentralRepoException if the Type's dbTableName has invalid
     *                        characters/format
     */
    @Messages({"CorrelationType.FILES.displayName=File MD5",
        "CorrelationType.DOMAIN.displayName=Domain",
        "CorrelationType.EMAIL.displayName=Email Address",
        "CorrelationType.PHONE.displayName=Phone Number",
        "CorrelationType.USBID.displayName=USB Device",
        "CorrelationType.SSID.displayName=Wireless Network",
        "CorrelationType.MAC.displayName=MAC Address",
        "CorrelationType.IMEI.displayName=IMEI Number",
        "CorrelationType.IMSI.displayName=IMSI Number",
        "CorrelationType.PROG_NAME.displayName=Installed Program",
        "CorrelationType.ICCID.displayName=ICCID Number",
        "CorrelationType.OS_ACCOUNT.displayName=Os Account"})
    public static List<CorrelationAttributeInstance.Type> getDefaultCorrelationTypes() throws CentralRepoException {
        List<CorrelationAttributeInstance.Type> defaultCorrelationTypes = new ArrayList<>();
         
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(FILES_TYPE_ID, Bundle.CorrelationType_FILES_displayName(), "file", true, true)); // NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(DOMAIN_TYPE_ID, Bundle.CorrelationType_DOMAIN_displayName(), "domain", true, true)); // NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(EMAIL_TYPE_ID, Bundle.CorrelationType_EMAIL_displayName(), "email_address", true, true)); // NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(PHONE_TYPE_ID, Bundle.CorrelationType_PHONE_displayName(), "phone_number", true, true)); // NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(USBID_TYPE_ID, Bundle.CorrelationType_USBID_displayName(), "usb_devices", true, true)); // NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(SSID_TYPE_ID, Bundle.CorrelationType_SSID_displayName(), "wireless_networks", true, true)); // NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(MAC_TYPE_ID, Bundle.CorrelationType_MAC_displayName(), "mac_address", true, true)); //NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(IMEI_TYPE_ID, Bundle.CorrelationType_IMEI_displayName(), "imei_number", true, true)); //NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(IMSI_TYPE_ID, Bundle.CorrelationType_IMSI_displayName(), "imsi_number", true, true)); //NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(ICCID_TYPE_ID, Bundle.CorrelationType_ICCID_displayName(), "iccid_number", true, true)); //NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(INSTALLED_PROGS_TYPE_ID, Bundle.CorrelationType_PROG_NAME_displayName(), "installed_programs", true, true)); //NON-NLS
        defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(OSACCOUNT_TYPE_ID, Bundle.CorrelationType_OS_ACCOUNT_displayName(), "os_accounts", true, true)); //NON-NLS
        
        // Create Correlation Types for Accounts.
        int correlationTypeId = ADDITIONAL_TYPES_BASE_ID;
        for (Account.Type type : Account.Type.PREDEFINED_ACCOUNT_TYPES) {
            // Skip Device account type - we dont want to correlate on those.
            // Skip Phone and Email accounts as there are already Correlation types defined for those.
            if (type != Account.Type.DEVICE && type != Account.Type.EMAIL && type != Account.Type.PHONE) {
                defaultCorrelationTypes.add(new CorrelationAttributeInstance.Type(correlationTypeId, type.getDisplayName(), type.getTypeName().toLowerCase() + "_acct", true, true)); //NON-NLS
                correlationTypeId++;
            }
        }

        return defaultCorrelationTypes;
    }

    /**
     * Correlation Types which determine which table to query in the CR
     */
    @SuppressWarnings("serial")
    public static class Type implements Serializable { // NOPMD Avoid short class names like Type

        private int typeId;
        private String displayName;
        private String dbTableName;
        private Boolean supported;
        private Boolean enabled;
        private final static String DB_NAMES_REGEX = "[a-z][a-z0-9_]*";

        /**
         *
         * @param typeId      Unique ID for this Correlation Type
         * @param displayName Name of this type displayed in the UI.
         * @param dbTableName Central repository db table where data of this
         *                    type is stored. Must start with a lowercase letter
         *                    and only contain lowercase letters, numbers, and
         *                    '_' characters.
         * @param supported   Is this Type currently supported
         * @param enabled     Is this Type currently enabled.
         */
        @Messages({"CorrelationAttributeInstance.nullName.message=Database name is null.",
            "CorrelationAttributeInstance.invalidName.message=Invalid database table name. Name must start with a lowercase letter and can only contain lowercase letters, numbers, and '_'."})
        public Type(int typeId, String displayName, String dbTableName, Boolean supported, Boolean enabled) throws CentralRepoException {
            if (dbTableName == null) {
                throw new CentralRepoException("dbTableName is null", Bundle.CorrelationAttributeInstance_nullName_message());
            }
            this.typeId = typeId;
            this.displayName = displayName;
            this.dbTableName = dbTableName;
            this.supported = supported;
            this.enabled = enabled;
            if (!Pattern.matches(DB_NAMES_REGEX, dbTableName)) {
                throw new CentralRepoException("Invalid database table name. Name must start with a lowercase letter and can only contain lowercase letters, numbers, and '_'.", Bundle.CorrelationAttributeInstance_invalidName_message()); // NON-NLS
            }
        }

        /**
         * Constructor for custom types where we do not know the Type ID until
         * the row has been entered into the correlation_types table in the
         * central repository.
         *
         * @param displayName Name of this type displayed in the UI.
         * @param dbTableName Central repository db table where data of this
         *                    type is stored Must start with a lowercase letter
         *                    and only contain lowercase letters, numbers, and
         *                    '_' characters.
         * @param supported   Is this Type currently supported
         * @param enabled     Is this Type currently enabled.
         */
        public Type(String displayName, String dbTableName, Boolean supported, Boolean enabled) throws CentralRepoException {
            this(-1, displayName, dbTableName, supported, enabled);
        }

        /**
         * Determine if 2 Type objects are equal
         *
         * @param that Type object for comparison.
         *
         * @return true or false
         */
        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            } else if (!(that instanceof CorrelationAttributeInstance.Type)) {
                return false;
            } else {
                return ((CorrelationAttributeInstance.Type) that).sameType(this);
            }
        }

        /**
         * Determines if the content of this artifact type object is equivalent
         * to the content of another artifact type object.
         *
         * @param that the other type
         *
         * @return true if it is the same type
         */
        private boolean sameType(CorrelationAttributeInstance.Type that) {
            return this.typeId == that.getId()
                    && Objects.equals(this.supported, that.isSupported())
                    && Objects.equals(this.enabled, that.isEnabled());
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.typeId);
            hash = 67 * hash + Objects.hashCode(this.supported);
            hash = 67 * hash + Objects.hashCode(this.enabled);
            return hash;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder(55);
            str.append("(id=")
                    .append(getId())
                    .append(", displayName=")
                    .append(getDisplayName())
                    .append(", dbTableName=")
                    .append(getDbTableName())
                    .append(", supported=")
                    .append(isSupported().toString())
                    .append(", enabled=")
                    .append(isEnabled().toString())
                    .append(')');
            return str.toString();
        }

        /**
         * @return the typeId
         */
        public int getId() {
            return typeId;
        }

        /**
         * @param typeId the typeId to set
         */
        public void setId(int typeId) {
            this.typeId = typeId;
        }

        /**
         * Check if this Artifact Type is supported.
         *
         * @return true or false
         */
        public Boolean isSupported() {
            return supported;
        }

        /**
         * Set this Artifact Type as supported or not supported.
         *
         * @param supported the supported to set
         */
        public void setSupported(Boolean supported) {
            this.supported = supported;
        }

        /**
         * Check if this Artifact Type is enabled.
         *
         * @return true or false
         */
        public Boolean isEnabled() {
            return enabled;
        }

        /**
         * Set this Artifact Type as enabled or not enabled.
         *
         * @param enabled the enabled to set
         */
        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return the displayName
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * @param displayName the displayName to set
         */
        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        /**
         * To support having different database tables for each Type, this field
         * provides the prefix/suffix of the table name, which allows us to
         * automatically compute the table names and index names.
         *
         * It is the prefix for the instances tables *_instances. (i.e.
         * file_instances) It is the suffix for the reference tables
         * reference_*. (i.e. reference_file)
         *
         * When custom Types are added in the future, they are already supported
         * by just giving the desired value for the table name for each custom
         * Type. Possibly having all custom Types use a common table name.
         *
         * @return the dbTableName
         */
        public String getDbTableName() {
            return dbTableName;
        }

        /**
         * To support having different database tables for each Type, this field
         * provides the prefix/suffix of the table name, which allows us to
         * automatically compute the table names and index names.
         *
         * It is the prefix for the instances tables *_instances. (i.e.
         * file_instances) It is the suffix for the reference tables
         * reference_*. (i.e. reference_file)
         *
         * When custom Types are added in the future, they are already supported
         * by just giving the desired value for the table name for each custom
         * Type. Possibly having all custom Types use a common table name. (i.e.
         * custom_instances)
         *
         * @param dbTableName the dbTableName to set. Must start with lowercase
         *                    letter and can only contain lowercase letters,
         *                    numbers, and '_' characters.
         *
         * @throws CentralRepoException if dbTableName contains invalid characters
         */
        public void setDbTableName(String dbTableName) throws CentralRepoException {
            if (!Pattern.matches(DB_NAMES_REGEX, dbTableName)) {
                throw new CentralRepoException("Invalid database table name. Name must start with a lowercase letter and can only contain lowercase letters, numbers, and '_'."); // NON-NLS
            }
            this.dbTableName = dbTableName;
        }
    }
}

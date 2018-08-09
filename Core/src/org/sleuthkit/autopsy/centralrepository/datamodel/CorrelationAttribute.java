/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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

/**
 * Represents a type and value pair that can be used for correlation. 
 * CorrelationAttributeInstances store information about the actual
 * occurrences of the attribute. 
 */
public class CorrelationAttribute implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ID;
    private String correlationValue;
    private Type correlationType;
    private final List<CorrelationAttributeInstance> artifactInstances;

    // Type ID's for Default Correlation Types
    public static final int FILES_TYPE_ID = 0;
    public static final int DOMAIN_TYPE_ID = 1;
    public static final int EMAIL_TYPE_ID = 2;
    public static final int PHONE_TYPE_ID = 3;
    public static final int USBID_TYPE_ID = 4;

    /**
     * Load the default correlation types
     * 
     * @throws EamDbException if the Type's dbTableName has invalid characters/format
     */
    @Messages({"CorrelationType.FILES.displayName=Files",
        "CorrelationType.DOMAIN.displayName=Domains",
        "CorrelationType.EMAIL.displayName=Email Addresses",
        "CorrelationType.PHONE.displayName=Phone Numbers",
        "CorrelationType.USBID.displayName=USB Devices"})
    public static List<CorrelationAttribute.Type> getDefaultCorrelationTypes() throws EamDbException {
        List<CorrelationAttribute.Type> DEFAULT_CORRELATION_TYPES = new ArrayList<>();
        DEFAULT_CORRELATION_TYPES.add(new CorrelationAttribute.Type(FILES_TYPE_ID, Bundle.CorrelationType_FILES_displayName(), "file", true, true)); // NON-NLS
        DEFAULT_CORRELATION_TYPES.add(new CorrelationAttribute.Type(DOMAIN_TYPE_ID, Bundle.CorrelationType_DOMAIN_displayName(), "domain", true, true)); // NON-NLS
        DEFAULT_CORRELATION_TYPES.add(new CorrelationAttribute.Type(EMAIL_TYPE_ID, Bundle.CorrelationType_EMAIL_displayName(), "email_address", true, true)); // NON-NLS
        DEFAULT_CORRELATION_TYPES.add(new CorrelationAttribute.Type(PHONE_TYPE_ID, Bundle.CorrelationType_PHONE_displayName(), "phone_number", true, true)); // NON-NLS
        DEFAULT_CORRELATION_TYPES.add(new CorrelationAttribute.Type(USBID_TYPE_ID, Bundle.CorrelationType_USBID_displayName(), "usb_devices", true, true)); // NON-NLS
        return DEFAULT_CORRELATION_TYPES;
    }

    public CorrelationAttribute(Type correlationType, String correlationValue) throws CorrelationAttributeNormalizationException {
        this.ID = "";
        this.correlationType = correlationType;
        this.correlationValue = CorrelationAttributeNormalizer.normalize(correlationType, correlationValue);
        this.artifactInstances = new ArrayList<>();
    }

    public Boolean equals(CorrelationAttribute otherArtifact) {
        return ((this.getID().equals(otherArtifact.getID()))
                && (this.getCorrelationType().equals(otherArtifact.getCorrelationType()))
                && (this.getCorrelationValue().equals(otherArtifact.getCorrelationValue()))
                && (this.getInstances().equals(otherArtifact.getInstances())));
    }

    @Override
    public String toString() {
        // NOTE: This string is currently being used in IngestEventsListener to detect if we have already seen
        // the value and type pair.  Be careful if this method is changed.  
        String result = this.getID()
                + this.getCorrelationType().toString()
                + this.getCorrelationValue();
        result = this.getInstances().stream().map((inst) -> inst.toString()).reduce(result, String::concat);
        return result;
    }

    /**
     * @return the ID
     */
    public String getID() {
        return ID;
    }

    /**
     * @param ID the ID to set
     */
    public void setID(String ID) {
        this.ID = ID;
    }

    /**
     * @return the correlationValue
     */
    public String getCorrelationValue() {
        return correlationValue;
    }

    /**
     * Set the correlation value.  Requires that the correlation type has already
     * been set (for data normalization purposes).
     * 
     * @param correlationValue the correlationValue to set
     */
    public void setCorrelationValue(String correlationValue) throws CorrelationAttributeNormalizationException {
        if(this.getCorrelationType() == null){
            throw new IllegalStateException("Correlation Type must be set before calling setCorrelationValue");
        }
        this.correlationValue = CorrelationAttributeNormalizer.normalize(this.getCorrelationType(), correlationValue);
    }

    /**
     * @return the correlation Type
     */
    public Type getCorrelationType() {
        return correlationType;
    }

    /**
     * @param correlationType the correlation Type to set
     */
    public void setCorrelationType(Type correlationType) {
        this.correlationType = correlationType;
    }

    /**
     * @return the List of artifactInstances; empty list of none have been
     *         added.
     */
    public List<CorrelationAttributeInstance> getInstances() {
        return new ArrayList<>(artifactInstances);
    }

    /**
     * Set the list of artifact instances
     * 
     * @param artifactInstances the List of artifactInstances to set.
     */
    public void setInstances(List<CorrelationAttributeInstance> artifactInstances) {
        this.artifactInstances.clear();
        if (null != artifactInstances) {
            this.artifactInstances.addAll(artifactInstances);
        }
    }

    /**
     * Add an artifact instance to the list
     * 
     * @param artifactInstance the instance to add
     */
    public void addInstance(CorrelationAttributeInstance artifactInstance) {
        this.artifactInstances.add(artifactInstance);
    }

    public static class Type implements Serializable {

        private int id;
        private String displayName;
        private String dbTableName;
        private Boolean supported;
        private Boolean enabled;
        private final String DB_NAMES_REGEX = "[a-z][a-z0-9_]*";

        /**
         * 
         * @param id            Unique ID for this Correlation Type
         * @param displayName   Name of this type displayed in the UI.
         * @param dbTableName   Central repository db table where data of this type is stored.
         *                      Must start with a lowercase letter and only contain
         *                      lowercase letters, numbers, and '_' characters.
         * @param supported     Is this Type currently supported
         * @param enabled       Is this Type currently enabled.
         */
        public Type(int id, String displayName, String dbTableName, Boolean supported, Boolean enabled) throws EamDbException {
            if(dbTableName == null) {
                throw new EamDbException("dbTableName is null");
            }
            this.id = id;
            this.displayName = displayName;
            this.dbTableName = dbTableName;
            this.supported = supported;
            this.enabled = enabled;
            if (!Pattern.matches(DB_NAMES_REGEX, dbTableName)) {
                throw new EamDbException("Invalid database table name. Name must start with a lowercase letter and can only contain lowercase letters, numbers, and '_'."); // NON-NLS
            }
        }

        /**
         * Constructor for custom types where we do not know the Type ID until
         * the row has been entered into the correlation_types table
         * in the central repository.
         * 
         * @param displayName   Name of this type displayed in the UI.
         * @param dbTableName   Central repository db table where data of this type is stored
         *                      Must start with a lowercase letter and only contain
         *                      lowercase letters, numbers, and '_' characters.
         * @param supported     Is this Type currently supported
         * @param enabled       Is this Type currently enabled.
         */
        public Type(String displayName, String dbTableName, Boolean supported, Boolean enabled) throws EamDbException {
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
            } else if (!(that instanceof CorrelationAttribute.Type)) {
                return false;
            } else {
                return ((CorrelationAttribute.Type) that).sameType(this);
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
        private boolean sameType(CorrelationAttribute.Type that) {
            return this.id == that.getId()
                    && Objects.equals(this.supported, that.isSupported())
                    && Objects.equals(this.enabled, that.isEnabled());
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.id);
            hash = 67 * hash + Objects.hashCode(this.supported);
            hash = 67 * hash + Objects.hashCode(this.enabled);
            return hash;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("(id=").append(getId());
            str.append(", displayName=").append(getDisplayName());
            str.append(", dbTableName=").append(getDbTableName());
            str.append(", supported=").append(isSupported().toString());
            str.append(", enabled=").append(isEnabled().toString());
            str.append(")");
            return str.toString();
        }

        /**
         * @return the id
         */
        public int getId() {
            return id;
        }

        /**
         * @param id the id to set
         */
        public void setId(int id) {
            this.id = id;
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
         * To support having different database tables for each Type,
         * this field provides the prefix/suffix of the table name,
         * which allows us to automatically compute the table names
         * and index names.
         * 
         * It is the prefix for the instances tables *_instances. (i.e. file_instances)
         * It is the suffix for the reference tables reference_*. (i.e. reference_file)
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
         * To support having different database tables for each Type,
         * this field provides the prefix/suffix of the table name,
         * which allows us to automatically compute the table names
         * and index names.
         * 
         * It is the prefix for the instances tables *_instances. (i.e. file_instances)
         * It is the suffix for the reference tables reference_*. (i.e. reference_file)
         * 
         * When custom Types are added in the future, they are already supported
         * by just giving the desired value for the table name for each custom
         * Type. Possibly having all custom Types use a common table name. (i.e. custom_instances)
         * 
         * @param dbTableName the dbTableName to set. Must start with lowercase letter
         *                      and can only contain lowercase letters, numbers, and '_' characters.
         * 
         * @throws EamDbException if dbTableName contains invalid characters
         */
        public void setDbTableName(String dbTableName) throws EamDbException {
            if (!Pattern.matches(DB_NAMES_REGEX, dbTableName)) {
                throw new EamDbException("Invalid database table name. Name must start with a lowercase letter and can only contain lowercase letters, numbers, and '_'."); // NON-NLS
            }
            this.dbTableName = dbTableName;
        }
    }
}

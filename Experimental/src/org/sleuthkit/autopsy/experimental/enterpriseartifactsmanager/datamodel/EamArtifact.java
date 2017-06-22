/*
 * Enterprise Artifacts Manager
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * Used to store info about a specific artifact.
 */
public class EamArtifact implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ID;
    private String artifactValue;
    private Type artifactType;
    private final List<EamArtifactInstance> artifactInstances;

    /**
     * Load the default correlation artifact types
     */
    public static List<EamArtifact.Type> getDefaultArtifactTypes() {
        List<EamArtifact.Type> DEFAULT_ARTIFACT_TYPES = new ArrayList<>();
        DEFAULT_ARTIFACT_TYPES.add(new EamArtifact.Type("FILES", true, true)); // NON-NLS
        DEFAULT_ARTIFACT_TYPES.add(new EamArtifact.Type("DOMAIN", true, false)); // NON-NLS
        DEFAULT_ARTIFACT_TYPES.add(new EamArtifact.Type("EMAIL", true, false)); // NON-NLS
        DEFAULT_ARTIFACT_TYPES.add(new EamArtifact.Type("PHONE", true, false)); // NON-NLS
        DEFAULT_ARTIFACT_TYPES.add(new EamArtifact.Type("USBID", true, false)); // NON-NLS
        return DEFAULT_ARTIFACT_TYPES;
    }

    public EamArtifact(Type artifactType, String artifactValue) {
        this.ID = "";
        this.artifactType = artifactType;
        this.artifactValue = artifactValue;
        this.artifactInstances = new ArrayList<>();
    }

    public Boolean equals(EamArtifact otherArtifact) {
        return ((this.getID().equals(otherArtifact.getID()))
                && (this.getArtifactType().equals(otherArtifact.getArtifactType()))
                && (this.getArtifactValue().equals(otherArtifact.getArtifactValue()))
                && (this.getInstances().equals(otherArtifact.getInstances())));
    }

    @Override
    public String toString() {
        String result = this.getID()
                + this.getArtifactType().toString()
                + this.getArtifactValue();
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
     * @return the artifactValue
     */
    public String getArtifactValue() {
        return artifactValue;
    }

    /**
     * @param artifactValue the artifactValue to set
     */
    public void setArtifactValue(String artifactValue) {
        this.artifactValue = artifactValue;
    }

    /**
     * @return the artifact Type
     */
    public Type getArtifactType() {
        return artifactType;
    }

    /**
     * @param artifactType the artifact Type to set
     */
    public void setArtifactType(Type artifactType) {
        this.artifactType = artifactType;
    }

    /**
     * @return the List of artifactInstances; empty list of none have been
     *         added.
     */
    public List<EamArtifactInstance> getInstances() {
        return new ArrayList<>(artifactInstances);
    }

    /**
     * @param artifactInstances the List of artifactInstances to set.
     */
    public void setInstances(List<EamArtifactInstance> artifactInstances) {
        this.artifactInstances.clear();
        if (null != artifactInstances) {
            this.artifactInstances.addAll(artifactInstances);
        }
    }

    /**
     * @param instance the instance to add
     */
    public void addInstance(EamArtifactInstance artifactInstance) {
        this.artifactInstances.add(artifactInstance);
    }

    public static class Type implements Serializable {

        private int id;
        private String name;
        private Boolean supported;
        private Boolean enabled;

        public Type(int id, String name, Boolean supported, Boolean enabled) {
            this.id = id;
            this.name = name;
            this.supported = supported;
            this.enabled = enabled;
        }

        public Type(String name, Boolean supported, Boolean enabled) {
            this(-1, name, supported, enabled);
        }

        /**
         * Determine if 2 Type objects are equal based on having the same
         * Type.name.
         *
         * @param otherType Type object for comparison.
         *
         * @return true or false
         */
        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            } else if (!(that instanceof EamArtifact.Type)) {
                return false;
            } else {
                return ((EamArtifact.Type) that).sameType(this);
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
        private boolean sameType(EamArtifact.Type that) {
            return this.id == that.getId()
                    && this.name.equals(that.getName())
                    && Objects.equals(this.supported, that.isSupported())
                    && Objects.equals(this.enabled, that.isEnabled());
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.id);
            hash = 67 * hash + Objects.hashCode(this.name);
            hash = 67 * hash + Objects.hashCode(this.supported);
            hash = 67 * hash + Objects.hashCode(this.enabled);
            return hash;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("(id=").append(id);
            str.append(", name=").append(name);
            str.append(", supported=").append(supported.toString());
            str.append(", enabled=").append(enabled.toString());
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
         * Get the name of this Artifact Type.
         *
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * Set the name of this Artifact Type
         *
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
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
    }
}

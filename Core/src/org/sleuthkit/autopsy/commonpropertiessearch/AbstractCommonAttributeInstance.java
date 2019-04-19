/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents an instance in either the CaseDB or CR that had a common match.
 * Different implementations know how to get the instance details from either
 * CaseDB or CR.
 *
 * Defines leaf-type nodes used in the Common Files Search results tree. Leaf
 * nodes, may describe common attributes which exist in the current case DB or
 * in the Central Repo. When a reference to the AbstractFile is lacking (such as
 * in the case that a common attribute is found in the Central Repo) not all
 * features of the Content Viewer, and context menu can be supported. Thus,
 * multiple types of leaf nodes are required to represent Common Attribute
 * Instance nodes.
 */
public abstract class AbstractCommonAttributeInstance {

    private final Long abstractFileObjectId;
    private final String caseName;
    private final String dataSource;

    // Reference to the AbstractFile instance in the current case
    // that matched on one of the common properties.
    protected AbstractFile abstractFile;

    /**
     * Create a leaf node for attributes found in files in the current case db.
     *
     * @param abstractFileReference file from which the common attribute was
     *                              found
     * @param dataSource            datasource where this attribute appears
     * @param caseName              case where this attribute appears
     */
    AbstractCommonAttributeInstance(Long abstractFileReference, String dataSource, String caseName) {
        this.abstractFileObjectId = abstractFileReference;
        this.caseName = caseName;
        this.dataSource = dataSource;
        this.abstractFile = null;
    }

    /**
     * Create a leaf node for attributes found in the central repo and not
     * available in the current case db.
     */
    AbstractCommonAttributeInstance() {
        this(-1L, "", "");
    }

    /**
     * Get the type of common attribute.
     *
     * @return
     */
    public abstract CorrelationAttributeInstance.Type getCorrelationAttributeInstanceType();

    /**
     * Get an AbstractFile for this instance if it can be retrieved from the
     * CaseDB.
     *
     * @return AbstractFile corresponding to this common attribute or null if it
     *         cannot be found (for example, in the event that this is a central
     *         repo file)
     */
    abstract AbstractFile getAbstractFile();

    /**
     * Create a list of leaf nodes, to be used to display a row in the tree
     * table
     *
     * @return leaf nodes for tree
     */
    abstract DisplayableItemNode[] generateNodes();

    /**
     * The name of the case where this common attribute is found.
     *
     * @return case name
     */
    String getCaseName() {
        return this.caseName;
    }

    /**
     * Get string name of the data source where this common attribute appears.
     *
     * @return data source name
     */
    public String getDataSource() {

        /**
         * Even though we could get this from the CR record or the AbstractFile,
         * we want to avoid getting it from the AbstractFile because it would be
         * an extra database roundtrip.
         */
        return this.dataSource;
    }

    /**
     * ObjectId of the AbstractFile that is equivalent to the file from which
     * this common attribute instance
     *
     * @return the abstractFileObjectId
     */
    public Long getAbstractFileObjectId() {
        return abstractFileObjectId;
    }

    /**
     * Use this to create an AbstractCommonAttributeInstanceNode of the
     * appropriate type. In any case, we'll get something which extends
     * DisplayableItemNode which can be used to populate the tree.
     *
     * If the common attribute in question could be derived from an AbstractFile
     * in the present SleuthkitCase, we can use an
     * IntraCaseCommonAttributeInstanceNode which enables extended functionality
     * in the context menu and in the content viewer.
     *
     * Otherwise, we will get an InterCaseCommonAttributeInstanceNode which
     * supports only baseline functionality.
     *
     * @param attribute       common file attribute instance form the central
     *                        repo
     * @param abstractFile    an AbstractFile from which the attribute instance
     *                        was found - applies to
     *                        CaseDbCommonAttributeInstance only
     * @param currentCaseName the name of the current case
     * @param nodeType        the type of the node to create for determining
     *                        which columns to add
     *
     * @return the appropriate leaf node for the results tree
     *
     * @throws TskCoreException
     */
    static DisplayableItemNode createNode(CorrelationAttributeInstance attribute, AbstractFile abstractFile, String currentCaseName, NODE_TYPE nodeType) throws TskCoreException {

        DisplayableItemNode leafNode;

        if (abstractFile == null) {
            leafNode = new CentralRepoCommonAttributeInstanceNode(attribute, nodeType);
        } else {
            final String abstractFileDataSourceName = abstractFile.getDataSource().getName();
            leafNode = new CaseDBCommonAttributeInstanceNode(abstractFile, currentCaseName, abstractFileDataSourceName, attribute.getCorrelationValue(), nodeType);
        }
        return leafNode;
    }

    /**
     * Enum for type of results being displayed in nodes.
     */
    public enum NODE_TYPE {
        //depending on the type of results displayed different columns will be necessary to display complete information
        CASE_NODE,
        COUNT_NODE;
    }
}

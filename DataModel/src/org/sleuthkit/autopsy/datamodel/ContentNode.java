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
package org.sleuthkit.autopsy.datamodel;

import java.sql.SQLException;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskException;

/**
 * Interface class that all Data nodes inherit from.
 * Provides basic information such as ID, parent ID, etc.
 */
public interface ContentNode {

    /**
     * Returns the programmatic name for this node. This is NOT the name to
     * display to users, or the plain name of the Content object - use
     * Node.getDisplayName() for that.
     *
     * @return name  the programmatic name for this node
     */
    public String getName();

    /**
     * Gets the ID of this node.
     *
     * @return ID  the ID of this node
     */
    public long getID();

    /**
     * Gets the row values for this node. The main purpose of this method is to
     * get the 'x' number of the row values for this node to set the width of each
     * column of the DataResult Table. Row values is the children and it's properties.
     *
     * @param rows              the number of rows we want to show
     * @return rowValues        the row values for this node.
     * @throws SQLException
     */
    public Object[][] getRowValues(int rows) throws SQLException;

    /**
     * Returns the content of this node.
     *
     * @return content  the content of this node (can be image, volume, directory, or file)
     */
    public Content getContent();

    /**
     * Returns full path to this node.
     *
     * @return the path of this node
     */
    public String[] getDisplayPath();

    /**
     * Returns full path to this node.
     *
     * @return the path of this node
     */
    public String[] getSystemPath();

    /**
     * Visitor pattern support.
     * 
     * @param <T> visitor return type
     * @param v visitor
     * @return visitor return value
     */
    public <T> T accept(ContentNodeVisitor<T> v);
}

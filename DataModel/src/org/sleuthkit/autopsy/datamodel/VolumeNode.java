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
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.sleuthkit.datamodel.Volume;

/**
 * This class is used to represent the "Node" for the volume.
 * Its child is the root directory of a file system
 */
public class VolumeNode extends AbstractContentNode<Volume> {

    /**
     * Helper so that the display name and the name used in building the path
     * are determined the same way.
     * @param vol Volume to get the name of
     * @return short name for the Volume
     */
    static String nameForVolume(Volume vol) {
        return "vol" + Long.toString(vol.getAddr());
    }

    /**
     * 
     * @param vol underlying Content instance
     */
    public VolumeNode(Volume vol) {
        super(vol);

        // set name, display name, and icon
        String volName = nameForVolume(vol);

        long end = vol.getStart() + vol.getSize();
        String tempVolName = volName + " (" + vol.getDescription() + ": " + vol.getStart() + "-" + end + ")";
        this.setDisplayName(tempVolName);

        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/vol-icon.png");
    }

    @Override
    public Object[][] getRowValues(int rows) throws SQLException {

        // how many rows are we returning
        int maxRows = rows;
        if (this.getChildren().getNodesCount() < maxRows) {
            maxRows = this.getChildren().getNodesCount();
        }
        Object[][] objs = new Object[maxRows][];

        for (int i = 0; i < maxRows; i++) {
            PropertySet[] props = this.getChildren().getNodeAt(i).getPropertySets();
            Property[] property = props[0].getProperties();
            objs[i] = new Object[property.length - 1]; // - 1 because we don't want to show the location property

            // name property
            try {
                objs[i][0] = property[0].getValue();
            } catch (Exception ex) {
                objs[i][0] = "n/a";
            }

            // the rest of the properties(not including the location property)
            for (int j = 1; j < property.length - 1; j++) {
                try {
                    objs[i][j] = property[j + 1].getValue();
                } catch (Exception ex) {
                    objs[i][j] = "n/a";
                }
            }
        }
        return objs;
    }

    @Override
    public int getFileIDColumn() {
        return 1;
    }

    /**
     * Right click action for volume node
     *
     * @param popup
     * @return
     */
    @Override
    public Action[] getActions(boolean popup) {
        return new Action[]{ //new ShowDetailAction("Volume Details", this.getName(), this),
                //new ShowDetailAction("File System Details", this.getName(), this)
                };
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty("Name", "Name", "no description", this.getDisplayName()));
        ss.put(new NodeProperty("ID", "ID", "no description", content.getAddr()));
        ss.put(new NodeProperty("Starting Sector", "Starting Sector", "no description", content.getStart()));
        ss.put(new NodeProperty("Length in Sectors", "Length in Sectors", "no description", content.getLength()));
        ss.put(new NodeProperty("Description", "Description", "no description", content.getDescription()));
        ss.put(new NodeProperty("Flags", "Flags", "no description", content.getFlagsAsString()));

        return s;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return v.visit(this);
    }
}

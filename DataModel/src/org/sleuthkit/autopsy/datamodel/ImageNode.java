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

import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.sleuthkit.datamodel.Image;

/**
 * This class is used to represent the "Node" for the image.
 * The children of this node are volumes.
 */
public class ImageNode extends AbstractContentNode<Image> {

    /**
     * Helper so that the display name and the name used in building the path
     * are determined the same way.
     * @param i Image to get the name of
     * @return short name for the Image
     */
    static String nameForImage(Image i) {
        return i.getName();
    }

    /**
     * @param img 
     */
    public ImageNode(Image img) {
        super(img);

        // set name, display name, and icon
        String imgName = nameForImage(img);
        this.setDisplayName(imgName);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hard-drive-icon.jpg");
    }

    @Override
    public Cookie getCookie(Class clazz) {
        Children ch = getChildren();

        if (clazz.isInstance(ch)) {
            return (Cookie) ch;
        }

        return super.getCookie(clazz);
    }

    /**
     * Right click action for this node
     *
     * @param context
     * @return
     */
    @Override
    public Action[] getActions(boolean context) {
        return new Action[]{ //            SystemAction.get( NewAction.class ),
                //            SystemAction.get( PasteAction.class )
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

        ss.put(new NodeProperty("Name", "Name", "no description", ""));
        // @@@ add more properties here...

        return s;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return v.visit(this);
    }
    
    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }
}

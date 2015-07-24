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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Image;
import java.lang.ref.SoftReference;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.datamodel.Content;

/**
 * Node that wraps around original node and adds the bitmap icon representing
 * the picture
 */
class ThumbnailViewNode extends FilterNode {

    private SoftReference<Image> iconCache = null;
    private int iconSize = ImageUtils.ICON_SIZE_MEDIUM;
    //private final BufferedImage defaultIconBI;

    /**
     * the constructor
     */
    ThumbnailViewNode(Node arg, int iconSize) {
        super(arg, Children.LEAF);
        this.iconSize = iconSize;
    }

    @Override
    public String getDisplayName() {
        if (super.getDisplayName().length() > 15) {
            return super.getDisplayName().substring(0, 15).concat("...");
        } else {
            return super.getDisplayName();
        }
    }

    @Override
    public Image getIcon(int type) {
        Image icon = null;
                  
        if (iconCache != null) {
            icon = iconCache.get();
        }

        if (icon == null) {
            Content content = this.getLookup().lookup(Content.class);

            if (content != null) {
                icon = ImageUtils.getThumbnail(content, iconSize);
            } else {
                icon = ImageUtils.getDefaultThumbnail();
            }

            iconCache = new SoftReference<>(icon);
        }
        
        return icon;
    }
    
    public void setIconSize(int iconSize) {
        this.iconSize = iconSize;
        iconCache = null;
    }

}

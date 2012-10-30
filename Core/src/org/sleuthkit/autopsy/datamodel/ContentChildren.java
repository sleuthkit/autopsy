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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;

/**
 * Class for Children of all ContentNodes. Handles creating child ContentNodes.
 * TODO consider a ContentChildren child factory
 */
class ContentChildren extends AbstractContentChildren {
    
    private static final Logger logger = Logger.getLogger(ContentChildren.class.getName());
    //private static final int MAX_CHILD_COUNT = 1000000;

    private Content parent;

    ContentChildren(Content parent) {
        super(); //initialize lazy behavior
        this.parent = parent;
    }

    @Override
    protected void addNotify() {
        super.addNotify();
        
        //TODO check global settings
        //if above limit, query and return subrange
        
        //StopWatch s2 = new StopWatch();
        //s2.start();
        //logger.log(Level.INFO, "GETTING CHILDREN CONTENT for parent: " + parent.getName());
        List<Content> children = ContentHierarchyVisitor.getChildren(parent);
        //s2.stop();
        //logger.log(Level.INFO, "GOT CHILDREN CONTENTS:" + children.size() + ", took: " + s2.getElapsedTime());
        
        // To not display LayoutFiles
//        Iterator<Content> it = children.iterator();
//        while(it.hasNext()) {
//            Content child = it.next();
//            if(child instanceof LayoutFile) {
//                it.remove();
//            }
//        }
        
        //limit number children
        //setKeys(children.subList(0, Math.min(children.size(), MAX_CHILD_COUNT)));

        setKeys(children);
    }

    @Override
    protected void removeNotify() {
        super.removeNotify();
        setKeys(Collections.EMPTY_SET);
    }
    
}

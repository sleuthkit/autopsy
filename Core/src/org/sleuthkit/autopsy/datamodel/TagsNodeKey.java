/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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

/**
 * Instances of this class act as keys for use by instances of the 
 * RootContentChildren class. RootContentChildren is a NetBeans child node 
 * factory built on top of the NetBeans Children.Keys class.  
 */
public class TagsNodeKey implements AutopsyVisitableItem {
    // Creation of a TagsNode object corresponding to TagsNodeKey object is done 
    // by a CreateAutopsyNodeVisitor dispatched from the AbstractContentChildren 
    // override of Children.Keys<T>.createNodes().
    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }    
}

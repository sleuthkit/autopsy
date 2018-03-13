/*
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
package org.sleuthkit.autopsy.datamodel;

import org.openide.nodes.Sheet;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Encapsulates data being pushed to Common Files component in top right pane.
 */
//public class CommonFileNode extends AbstractFsContentNode<AbstractFile> {
public class CommonFileNode extends AbstractNode {
    //TODO goto AbstractNode rather than AbstractFsContentNode<T>...
    
    public CommonFileNode(AbstractFile fsContent) {
        super(fsContent);
    }

    @Override
    protected Sheet createSheet(){
        return null;    //TODO
    }
    
    //ContentTagNode as an example
    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isLeafTypeNode() {//TODO return true
        return true;
    }

    @Override
    public String getItemType() {//TODO getClass.getCanonicalName
        return this.getClass().getCanonicalName();
    }    
}

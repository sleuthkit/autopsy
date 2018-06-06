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
package org.sleuthkit.autopsy.datamodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepositoryFile;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Used by the Common Files search feature to encapsulate instances of a given 
 * MD5s matched in the search.  These nodes will be children of <code>Md5Node</code>s.
 * 
 * Use this type for files which are not in the current case, but from the 
 * Central Repo.  Contrast with <code>SleuthkitCase</code> which should be used 
 * when the FileInstance was found in the case presently open in Autopsy.
 */
public class CentralRepositoryFileInstanceNode extends DisplayableItemNode {

    public CentralRepositoryFileInstanceNode(CentralRepositoryFile file, AbstractFile md5Reference) {
        super(Children.LEAF, Lookups.fixed(file, md5Reference));
    }
    
    @Override
    public Action[] getActions(boolean context){
        List<Action> actionsList = new ArrayList<>();
        
        actionsList.addAll(Arrays.asList(super.getActions(true)));
        //TODO probably can support more than just this
        
        return actionsList.toArray(new Action[actionsList.size()]);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public String getItemType() {
        //objects of type FileNode will co-occur in the treetable with objects
        //  of this type and they will need to provide the same key
        return SleuthkitCaseFileInstanceNode.class.getName();
    }
}

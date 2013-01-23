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

import java.util.Collection;
import java.util.Collections;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Children implementation for the root node of a ContentNode tree. Accepts a
 * list of root Content objects for the tree.
 */
public class RootContentChildren extends AbstractContentChildren<Object> {
    private Collection<? extends Object> contentKeys;
    
    /**
     * @param contentKeys root Content objects for the Node tree
     */
    public RootContentChildren(Collection<? extends Object> contentKeys) {
        super();
        this.contentKeys = contentKeys;
    }
    
    @Override
    protected void addNotify() {
        setKeys(contentKeys);
    }
    
    @Override
    protected void removeNotify() {
        setKeys(Collections.<Object>emptySet());
    }
    
    //TODO use visitor
    //TODO this will be removed, Children should be listening for interesting 
    //events from datamodel and calling refresh / refreshKey() themselves
    public void refreshKeys(BlackboardArtifact.ARTIFACT_TYPE... types) {
        for (Object o : contentKeys) {
            for (BlackboardArtifact.ARTIFACT_TYPE type : types) {
                switch (type) {
                    case TSK_HASHSET_HIT:
                        if (o instanceof HashsetHits)
                            this.refreshKey(o);
                        break;
                    case TSK_KEYWORD_HIT:
                        if (o instanceof KeywordHits)
                            this.refreshKey(o);
                        break;
                    case TSK_EMAIL_MSG:
                        if (o instanceof EmailExtracted)
                            this.refreshKey(o);
                        break;
                        
                        //TODO check
                    case TSK_TAG_FILE:
                        if (o instanceof Tags)
                            this.refreshKey(o);
                        break;
                        
                        //TODO check
                     case TSK_TAG_ARTIFACT:
                        if (o instanceof Tags)
                            this.refreshKey(o);
                        break;
                    default:
                        if (o instanceof ExtractedContent)
                            this.refreshKey(o);
                        break;
                }
            }
            if (types.length == 0) {
                if (o instanceof HashsetHits)
                    this.refreshKey(o);
                else if (o instanceof KeywordHits)
                    this.refreshKey(o);
                else if (o instanceof EmailExtracted)
                    this.refreshKey(o);
                else if (o instanceof Tags)
                    this.refreshKey(o);
                else if (o instanceof ExtractedContent)
                    this.refreshKey(o);
            }
        }
    }
}

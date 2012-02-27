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

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskException;

/**
 * StringContent object for a blackboard artifact, that can be looked up and used
 * to display text for the DataContent viewers
 * @author alawrence
 */
public class ArtifactStringContent implements StringContent{
    BlackboardArtifact wrapped;
    static final Logger logger = Logger.getLogger(ArtifactStringContent.class.getName());
    
    public ArtifactStringContent(BlackboardArtifact art){
        wrapped = art;
    }
    
    @Override
    public String getString() {
        try{
        StringBuilder buffer = new StringBuilder();
        buffer.append(wrapped.getDisplayName());
        buffer.append("\n");
        for(BlackboardAttribute attr : wrapped.getAttributes()){
            buffer.append(attr.getAttributeTypeDisplayName()); 
            buffer.append(": ");
            switch(attr.getValueType()){
                case STRING:
                    buffer.append(attr.getValueString());
                    break;
                case INTEGER:
                    buffer.append(attr.getValueInt());
                    break;
                case LONG:
                    buffer.append(attr.getValueLong());
                    break;
                case DOUBLE:
                    buffer.append(attr.getValueDouble());
                    break;
                case BYTE:
                    buffer.append(Arrays.toString(attr.getValueBytes()));
                    break;
                    
            }
            buffer.append(": ");
            buffer.append(attr.getContext());
            buffer.append("\n");
        }
        return buffer.toString();
        }
        catch (TskException ex) {
            return "Error getting content";
        }    
    }
    
}

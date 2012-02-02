/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.util.Arrays;
import org.openide.util.Exceptions;
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

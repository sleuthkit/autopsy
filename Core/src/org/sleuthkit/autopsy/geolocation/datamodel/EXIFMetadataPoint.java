/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.geolocation.datamodel;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extends SimplePoint for TSK_EXIF_METADATA artifacts.
 * 
 */
public class EXIFMetadataPoint extends SimplePoint{
    private AbstractFile imageFile;
    
    /**
     * Construct a EXIF point
     * 
     * @param artifact 
     */
    EXIFMetadataPoint(BlackboardArtifact artifact) {
        super(artifact);
    }
    
    @Override
    public void initPoint() throws TskCoreException{
        super.initPoint();

        setTimestamp(getLong(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED));
       
        BlackboardArtifact artifact = getArtifact();
        if(artifact != null) {
            imageFile = artifact.getSleuthkitCase().getAbstractFileById(artifact.getObjectID());
        }
        
        setLabel(imageFile.getName());
    }
    
    /**
     * Get the image for this point.
     * 
     * @return Return the AbstractFile image for the EXIF_METADATA artifact.
     */
    public AbstractFile getImage() {
        return imageFile;
    }
}

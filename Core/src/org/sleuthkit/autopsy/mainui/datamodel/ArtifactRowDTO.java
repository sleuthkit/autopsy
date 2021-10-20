/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.List;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/**
 * Abstract base class for a row result representing a BlackboardArtifact.
 * 
 * @param <T> 
 */
public abstract class ArtifactRowDTO<T extends BlackboardArtifact> extends BaseRowDTO{
    
    private final T artifact;
    private final Content srcContent;
    private final Content linkedFile;
    private final boolean isTimelineSupported;
    
    ArtifactRowDTO(T artifact,  Content srcContent, Content linkedFile, boolean isTimelineSupported, List<Object> cellValues, String typeId, long id) {
        super(cellValues, typeId, id);
        this.artifact = artifact;
        this.srcContent = srcContent;
        this.linkedFile = linkedFile;
        this.isTimelineSupported = isTimelineSupported;
    }
    
    /**
     * Returns the artifact for this row result.
     * 
     * @return 
     */
    public T getArtifact() {
        return artifact;
    }
    
    /**
     * Returns the source content for the artifact row.
     * 
     * @return The source content.
     */
    public Content getSrcContent() {
        return srcContent;
    }

    /**
     * Returns the file linked with this artifact row.
     * 
     * @return The linked file.
     */
    public Content getLinkedFile() {
        return linkedFile;
    }

    /**
     * Returns whether the artifact supported timeline events.
     * 
     * @return True if timeline is supported for this artifact.
     */
    public boolean isTimelineSupported() {
        return isTimelineSupported;
    }
}

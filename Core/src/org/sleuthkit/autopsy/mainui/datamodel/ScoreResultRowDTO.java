/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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

/**
 * Score results dto.
 */
public class ScoreResultRowDTO extends BaseRowDTO {
    private static final String TYPE_ID = "SCORE_RESULT_ROW";

    public static String getTypeIdForClass() {
        return TYPE_ID;
    }
    
    
    private final FileRowDTO fileDTO;
    private final DataArtifactRowDTO artifactDTO;
    private final BlackboardArtifact.Type artifactType;
   

    public ScoreResultRowDTO(FileRowDTO fileDTO, List<Object> cellValues, long id) {
        super(cellValues, TYPE_ID, id);
        this.fileDTO = fileDTO;
        this.artifactDTO = null;
        this.artifactType = null;
    }

    public ScoreResultRowDTO(DataArtifactRowDTO artifactDTO, BlackboardArtifact.Type artifactType, List<Object> cellValues, long id) {
        super(cellValues, TYPE_ID, id);
        this.fileDTO = null;
        this.artifactDTO = artifactDTO;
        this.artifactType = artifactType;
    }

    public FileRowDTO getFileDTO() {
        return fileDTO;
    }

    public DataArtifactRowDTO getArtifactDTO() {
        return artifactDTO;
    }

    public BlackboardArtifact.Type getArtifactType() {
        return artifactType;
    }
}

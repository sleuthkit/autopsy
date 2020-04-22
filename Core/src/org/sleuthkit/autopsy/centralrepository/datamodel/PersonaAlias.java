/*
 * Central Repository
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import org.sleuthkit.datamodel.Examiner;

/**
 * This class abstracts an alias assigned to a Persona.
 * A Persona may have multiple aliases.
 * 
 */
public class PersonaAlias {
    
    private final long personaId;
    private final String alias;
    private final String justification;
    private final Persona.Confidence confidence;
    private final long dateAdded;
    private final Examiner examiner;

    public long getPersonaId() {
        return personaId;
    }

    public String getAlias() {
        return alias;
    }

    public String getJustification() {
        return justification;
    }

    public Persona.Confidence getConfidence() {
        return confidence;
    }

    public long getDateAadded() {
        return dateAdded;
    }

    public Examiner getExaminer() {
        return examiner;
    }
    
    public PersonaAlias(long personaId, String alias, String justification, Persona.Confidence confidence, long dateAdded, Examiner examiner) {
        this.personaId = personaId;
        this.alias = alias;
        this.justification = justification;
        this.confidence = confidence;
        this.dateAdded = dateAdded;
        this.examiner = examiner;
    }
    
    
}

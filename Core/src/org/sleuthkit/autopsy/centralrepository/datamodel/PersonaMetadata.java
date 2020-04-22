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
 * This class abstracts metadata associated with a Persona.
 * Metadata is in the form of a name/value pair.
 * 
 * A Persona may have zero or more metadata.
 * 
 */
public class PersonaMetadata {
    
    private final long personaId;
    private final String name;
    private final String value;
    private final String justification;
    private final Persona.Confidence confidence;
    private final long dateAdded;
    private final Examiner examiner;

    public long getPersonaId() {
        return personaId;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getJustification() {
        return justification;
    }

    public Persona.Confidence getConfidence() {
        return confidence;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public Examiner getExaminer() {
        return examiner;
    }
    
    public PersonaMetadata(long personaId, String name, String value, String justification, Persona.Confidence confidence, long dateAdded, Examiner examiner) {
        this.personaId = personaId;
        this.name = name;
        this.value = value;
        this.justification = justification;
        this.confidence = confidence;
        this.dateAdded = dateAdded;
        this.examiner = examiner;
    }
    
}

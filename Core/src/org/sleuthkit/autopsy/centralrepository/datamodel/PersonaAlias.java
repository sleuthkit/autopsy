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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.datamodel.Examiner;
import org.sleuthkit.datamodel.SleuthkitCase;

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
    
     /**
     * Creates an alias for the specified Persona.
     *
     * @param persona Persona for which the alias is being added.
     * @param alias Alias name.
     * @param justification Reason for assigning the alias, may be null.
     * @param confidence Confidence level.
     *
     * @return PersonaAlias
     * @throws CentralRepoException If there is an error in creating the alias.
     */
    static PersonaAlias addPersonaAlias(Persona persona, String alias, String justification, Persona.Confidence confidence) throws CentralRepoException {

        Examiner examiner = CentralRepository.getInstance().getCurrentCentralRepoExaminer();

        Instant instant = Instant.now();
        Long timeStampMillis = instant.toEpochMilli();

        String insertClause = " INTO persona_alias (persona_id, alias, justification, confidence_id, date_added, examiner_id ) "
                + "VALUES ( "
                + persona.getId() + ", "
                + "'" + alias + "', "
                + "'" + ((StringUtils.isBlank(justification) ? "" : SleuthkitCase.escapeSingleQuotes(justification))) + "', "
                + confidence.getLevelId() + ", "
                + timeStampMillis.toString() + ", "
                + examiner.getId()
                + ")";

        CentralRepository.getInstance().executeInsertSQL(insertClause);
        return new PersonaAlias(persona.getId(), alias, justification, confidence, timeStampMillis, examiner);
    }
    
     /**
     * Callback to process a Persona aliases query.
     */
    static class PersonaAliasesQueryCallback implements CentralRepositoryDbQueryCallback {

        private final Collection<PersonaAlias> personaAliases = new ArrayList<>();

        @Override
        public void process(ResultSet rs) throws SQLException {

            while (rs.next()) {
                Examiner examiner = new Examiner(
                        rs.getInt("examiner_id"),
                        rs.getString("login_name"),
                        rs.getString("display_name"));

                PersonaAlias alias = new PersonaAlias(
                        rs.getLong("persona_id"),
                        rs.getString("alias"),
                        rs.getString("justification"),
                        Persona.Confidence.fromId(rs.getInt("confidence_id")),
                        Long.parseLong(rs.getString("date_added")),
                        examiner);

                personaAliases.add(alias);
            }
        }

        Collection<PersonaAlias> getAliases() {
            return Collections.unmodifiableCollection(personaAliases);
        }
    };
    
    /**
     * Gets all aliases for the persona with specified id.
     *
     * @param personaId Id of the persona for which to get the aliases.
     * @return A collection of aliases, may be empty.
     *
     * @throws CentralRepoException If there is an error in retrieving aliases.
     */
    public static Collection<PersonaAlias> getPersonaAliases(long personaId) throws CentralRepoException {
        String queryClause = "SELECT pa.id, pa.persona_id, pa.alias, pa.justification, pa.confidence_id, pa.date_added, pa.examiner_id, e.login_name, e.display_name "
                + "FROM persona_alias as pa "
                + "INNER JOIN examiners as e ON e.id = pa.examiner_id ";

        PersonaAliasesQueryCallback queryCallback = new PersonaAliasesQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);

        return queryCallback.getAliases();
    }
    
}

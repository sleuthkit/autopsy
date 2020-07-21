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
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * This class abstracts an alias assigned to a Persona. A Persona may have
 * multiple aliases.
 *
 */
public class PersonaAlias {

    private static final String SELECT_QUERY_BASE
            = "SELECT pa.id, pa.persona_id, pa.alias, pa.justification, pa.confidence_id, pa.date_added, pa.examiner_id, e.login_name, e.display_name "
            + "FROM persona_alias as pa "
            + "INNER JOIN examiners as e ON e.id = pa.examiner_id ";

    private final long id;
    private final long personaId;
    private final String alias;
    private final String justification;
    private final Persona.Confidence confidence;
    private final long dateAdded;
    private final CentralRepoExaminer examiner;

    public long getId() {
        return id;
    }

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

    public CentralRepoExaminer getExaminer() {
        return examiner;
    }

    public PersonaAlias(long id, long personaId, String alias, String justification, Persona.Confidence confidence, long dateAdded, CentralRepoExaminer examiner) {
        this.id = id;
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

        CentralRepoExaminer examiner = getCRInstance().getOrInsertExaminer(System.getProperty("user.name"));

        Instant instant = Instant.now();
        Long timeStampMillis = instant.toEpochMilli();

        String insertSQL = "INSERT INTO persona_alias (persona_id, alias, justification, confidence_id, date_added, examiner_id ) "
                + " VALUES ( ?, ?, ?, ?, ?, ?)";

        List<Object> params = new ArrayList<>();
        params.add(persona.getId());
        params.add(alias);
        params.add(StringUtils.isBlank(justification) ? "" : justification);
        params.add(confidence.getLevelId());
        params.add(timeStampMillis);
        params.add(examiner.getId());

        getCRInstance().executeCommand(insertSQL, params);

        String queryClause = SELECT_QUERY_BASE
                + "WHERE pa.persona_id = ?"
                + " AND pa.alias = ?"
                + " AND pa.date_added = ?"
                + " AND pa.examiner_id = ?";

        List<Object> queryParams = new ArrayList<>();
        queryParams.add(persona.getId());
        queryParams.add(alias);
        queryParams.add(timeStampMillis);
        queryParams.add(examiner.getId());

        PersonaAliasesQueryCallback queryCallback = new PersonaAliasesQueryCallback();
        getCRInstance().executeQuery(queryClause, queryParams, queryCallback);

        Collection<PersonaAlias> aliases = queryCallback.getAliases();
        if (aliases.size() != 1) {
            throw new CentralRepoException("Alias add query failed");
        }

        return aliases.iterator().next();
    }

    /**
     * Removes a PersonaAlias.
     *
     * @param alias Alias to remove.
     *
     * @throws CentralRepoException If there is an error in removing the alias.
     */
    static void removePersonaAlias(PersonaAlias alias) throws CentralRepoException {
        String deleteSQL = " DELETE FROM persona_alias WHERE id = ?";

        List<Object> params = new ArrayList<>();
        params.add(alias.getId());

        getCRInstance().executeCommand(deleteSQL, params);
    }

    /**
     * Modifies a PesronaAlias.
     *
     * @param alias Alias to modify.
     *
     * @throws CentralRepoException If there is an error in modifying the alias.
     */
    static void modifyPersonaAlias(PersonaAlias alias, Persona.Confidence confidence, String justification) throws CentralRepoException {
        CentralRepository cr = CentralRepository.getInstance();

        if (cr == null) {
            throw new CentralRepoException("Failed to modify persona alias, Central Repo is not enabled");
        }

        String updateClause = "UPDATE persona_alias SET confidence_id = ?, justification = ? WHERE id = ?";

        List<Object> params = new ArrayList<>();
        params.add(confidence.getLevelId());
        params.add(StringUtils.isBlank(justification) ? "" : justification);
        params.add(alias.getId());

        cr.executeCommand(updateClause, params);
    }

    /**
     * Callback to process a Persona aliases query.
     */
    static class PersonaAliasesQueryCallback implements CentralRepositoryDbQueryCallback {

        private final Collection<PersonaAlias> personaAliases = new ArrayList<>();

        @Override
        public void process(ResultSet rs) throws SQLException {

            while (rs.next()) {
                CentralRepoExaminer examiner = new CentralRepoExaminer(
                        rs.getInt("examiner_id"),
                        rs.getString("login_name"));

                PersonaAlias alias = new PersonaAlias(
                        rs.getLong("id"),
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
        String queryClause = SELECT_QUERY_BASE
                + "WHERE pa.persona_id = ?";

        List<Object> params = new ArrayList<>();
        params.add(personaId);

        PersonaAliasesQueryCallback queryCallback = new PersonaAliasesQueryCallback();
        getCRInstance().executeQuery(queryClause, params, queryCallback);

        return queryCallback.getAliases();
    }

    /**
     * Wraps the call to CentralRepository.getInstance() throwing an exception
     * if instance is null;
     *
     * @return Instance of CentralRepository
     *
     * @throws CentralRepoException
     */
    private static CentralRepository getCRInstance() throws CentralRepoException {
        CentralRepository instance = CentralRepository.getInstance();

        if (instance == null) {
            throw new CentralRepoException("Failed to get instance of CentralRespository, CR was null");
        }

        return instance;
    }
}

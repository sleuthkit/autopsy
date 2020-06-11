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
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * This class abstracts a persona.
 *
 * An examiner may create a persona from an account.
 *
 */
public class Persona {

    /**
     * Defines level of confidence in assigning a persona to an account.
     */
    public enum Confidence {
        UNKNOWN(1, "Unknown"),
        LOW(2, "Low confidence"),
        MEDIUM(3, "Medium confidence"),
        HIGH(4, "High confidence"),
        DERIVED(5, "Derived directly");

        private final String name;
        private final int level_id;

        Confidence(int level, String name) {
            this.name = name;
            this.level_id = level;

        }

        @Override
        public String toString() {
            return name;
        }

        public int getLevelId() {
            return this.level_id;
        }

        static Confidence fromId(int value) {
            for (Confidence confidence : Confidence.values()) {
                if (confidence.getLevelId() == value) {
                    return confidence;
                }
            }
            return Confidence.UNKNOWN;
        }

    }

    /**
     * Defines status of a persona.
     */
    public enum PersonaStatus {

        UNKNOWN(1, "Unknown"),
        ACTIVE(2, "Active"),
        MERGED(3, "Merged"),
        SPLIT(4, "Split"),
        DELETED(5, "Deleted");

        private final String description;
        private final int status_id;

        PersonaStatus(int status, String description) {
            this.status_id = status;
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }

        public int getStatusId() {
            return this.status_id;
        }

        static PersonaStatus fromId(int value) {
            for (PersonaStatus status : PersonaStatus.values()) {
                if (status.getStatusId() == value) {
                    return status;
                }
            }
            return PersonaStatus.UNKNOWN;
        }
    }

    // primary key in the Personas table in CR database
    private final long id;
    private final String uuidStr;
    private final String name;
    private final String comment;
    private final long createdDate;
    private final long modifiedDate;
    private final PersonaStatus status;
    private final CentralRepoExaminer examiner;
    
    @NbBundle.Messages("Persona.defaultName=Unnamed")
    public static String getDefaultName() {
        return Bundle.Persona_defaultName();
    }

    public long getId() {
        return id;
    }

    public String getUuidStr() {
        return uuidStr;
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public long getModifiedDate() {
        return modifiedDate;
    }

    public PersonaStatus getStatus() {
        return status;
    }

    public CentralRepoExaminer getExaminer() {
        return examiner;
    }

    Persona(long id, String uuidStr, String name, String comment, long created_date, long modified_date, PersonaStatus status, CentralRepoExaminer examiner) {
        this.id = id;
        this.uuidStr = uuidStr;
        this.name = name;
        this.comment = comment;
        this.createdDate = created_date;
        this.modifiedDate = modified_date;
        this.status = status;
        this.examiner = examiner;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + (int) (this.id ^ (this.id >>> 32));
        hash = 67 * hash + Objects.hashCode(this.uuidStr);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Persona other = (Persona) obj;
        if (this.id != other.getId()) {
            return false;
        }
        return this.uuidStr.equalsIgnoreCase(other.getUuidStr());
    }

    /**
     * Creates a Persona and associates the specified account with it.
     *
     * @param personaName Persona name.
     * @param comment Comment to associate with persona, may be null.
     * @param status Persona status
     * @param account Account for which the persona is being created.
     * @param justification Justification for why this account belongs to this
     * persona, may be null.
     * @param confidence Confidence level for this association of Persona &
     * account.
     *
     * @return Persona Persona created.
     * @throws CentralRepoException If there is an error creating the Persona.
     */
    public static Persona createPersonaForAccount(String personaName, String comment, PersonaStatus status, CentralRepoAccount account, String justification, Persona.Confidence confidence) throws CentralRepoException {
        Persona persona = createPersona(personaName, comment, status);
        persona.addAccount(account, justification, confidence);
        return persona;
    }

    /**
     * Inserts a row in the Persona tables.
     *
     * @param name Persona name, may be null - default name is used in that
     * case.
     * @param comment Comment to associate with persona, may be null.
     * @param status Persona status.
     *
     * @return Persona corresponding to the row inserted in the personas table.
     *
     * @throws CentralRepoException If there is an error in adding a row to
     * personas table.
     */
    private static Persona createPersona(String name, String comment, PersonaStatus status) throws CentralRepoException {
        // generate a UUID for the persona
        String uuidStr = UUID.randomUUID().toString();
        CentralRepoExaminer examiner = CentralRepository.getInstance().getOrInsertExaminer(System.getProperty("user.name"));

        Instant instant = Instant.now();
        Long timeStampMillis = instant.toEpochMilli();
        String insertClause = " INTO personas (uuid, comment, name, created_date, modified_date, status_id, examiner_id ) "
                + "VALUES ( '" + uuidStr + "', "
                + "'" + ((StringUtils.isBlank(comment) ? "" : SleuthkitCase.escapeSingleQuotes(comment))) + "',"
                + "'" + ((StringUtils.isBlank(name) ? getDefaultName() : SleuthkitCase.escapeSingleQuotes(name))) + "',"
                + timeStampMillis.toString() + ","
                + timeStampMillis.toString() + ","
                + status.getStatusId() + ","
                + examiner.getId()
                + ")";

        CentralRepository.getInstance().executeInsertSQL(insertClause);
        return getPersonaByUUID(uuidStr);
    }
    
    /**
     * Sets the name of this persona
     *
     * @param name The new name.
     * 
     * @throws CentralRepoException If there is an error.
     */
    public void setName(String name) throws CentralRepoException {
        String updateClause = "UPDATE personas SET name = \"" + name + "\" WHERE id = " + id;
        CentralRepository.getInstance().executeUpdateSQL(updateClause);
    }

    /**
     * Associates an account with a persona by creating a row in the
     * PersonaAccounts table.
     *
     * @param account Account to add to persona.
     * @param justification Reason for adding the account to persona, may be
     * null.
     * @param confidence Confidence level.
     *
     * @return PersonaAccount
     * @throws CentralRepoException If there is an error.
     */
    public PersonaAccount addAccount(CentralRepoAccount account, String justification, Persona.Confidence confidence) throws CentralRepoException {
        return PersonaAccount.addPersonaAccount(this, account, justification, confidence);
    }
    
    /**
     * Removes the given PersonaAccount (persona/account association)
     *
     * @param account account to remove
     *
     * @throws CentralRepoException If there is an error in querying the
     * Personas table.
     */
    public void removeAccount(PersonaAccount account) throws CentralRepoException {
        PersonaAccount.removePersonaAccount(account.getId());
    }
    
    /**
     * Marks this persona as deleted
     */
    public void delete() throws CentralRepoException {
        String deleteSQL = "UPDATE personas SET status_id = " + PersonaStatus.DELETED.status_id + " WHERE id = " + this.id;
        CentralRepository.getInstance().executeUpdateSQL(deleteSQL);
    }

    /**
     * Callback to process a Persona query from the persona table.
     */
    private static class PersonaQueryCallback implements CentralRepositoryDbQueryCallback {

        private final Collection<Persona> personaList =  new ArrayList<>();

        @Override
        public void process(ResultSet rs) throws SQLException {

            while (rs.next()) {
                CentralRepoExaminer examiner = new CentralRepoExaminer(
                        rs.getInt("examiner_id"),
                        rs.getString("login_name"));

                PersonaStatus status = PersonaStatus.fromId(rs.getInt("status_id"));
                Persona persona = new Persona(
                        rs.getInt("id"),
                        rs.getString("uuid"),
                        rs.getString("name"),
                        rs.getString("comment"),
                        Long.parseLong(rs.getString("created_date")),
                        Long.parseLong(rs.getString("modified_date")),
                        status,
                        examiner
                );
                
                personaList.add(persona);
            }
        }

        Collection<Persona> getPersonas() {
            return Collections.unmodifiableCollection(personaList);
        }
    };

    // Partial query string to select from personas table, 
    // just supply the where clause.
    private static final String PERSONA_QUERY = 
                  "SELECT p.id, p.uuid, p.name, p.comment, p.created_date, p.modified_date, p.status_id, p.examiner_id, e.login_name, e.display_name "
                + "FROM personas as p "
                + "INNER JOIN examiners as e ON e.id = p.examiner_id ";
              
     
    /**
     * Gets the row from the Personas table with the given UUID, creates and
     * returns the Persona from that data.
     *
     * @param uuid Persona UUID to match.
     * @return Persona matching the given UUID, may be null if no match is
     * found.
     *
     * @throws CentralRepoException If there is an error in querying the
     * Personas table.
     */
    private static Persona getPersonaByUUID(String uuid) throws CentralRepoException {

        String queryClause = 
                PERSONA_QUERY
                + "WHERE p.uuid = '" + uuid + "'";

        PersonaQueryCallback queryCallback = new PersonaQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);

        Collection<Persona> personas = queryCallback.getPersonas();
        
        return personas.isEmpty() ? null : personas.iterator().next();
    }

    /**
     * Gets the rows from the Personas table with matching name. 
     * Persona marked as DELETED are not returned.
     *
     * @param partialName Name substring to match.
     * @return Collection of personas matching the given name substring, may be
     * empty if no match is found.
     *
     * @throws CentralRepoException If there is an error in querying the
     * Personas table.
     */
    public static Collection<Persona> getPersonaByName(String partialName) throws CentralRepoException {

        String queryClause = PERSONA_QUERY
                + "WHERE p.status_id != " + PersonaStatus.DELETED.status_id + 
                " AND LOWER(p.name) LIKE " + "LOWER('%" + partialName + "%')" ;

        PersonaQueryCallback queryCallback = new PersonaQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);

        return queryCallback.getPersonas();
    }
    
    /**
     * Gets the rows from the Personas table where persona accounts' names are
     * similar to the given one. Persona marked as DELETED are not returned.
     *
     * @param partialName Name substring to match.
     * @return Collection of personas matching the given name substring, may be
     * empty if no match is found.
     *
     * @throws CentralRepoException If there is an error in querying the
     * Personas table.
     */
    public static Collection<Persona> getPersonaByAccountIdentifierLike(String partialName) throws CentralRepoException {
        String queryClause = "SELECT DISTINCT accounts.id as a_id,"
                + "p.id, p.uuid, p.name, p.comment, p.created_date, p.modified_date, p.status_id, p.examiner_id, e.login_name, e.display_name"
                + " FROM accounts"
                + " JOIN persona_accounts as pa ON pa.account_id = accounts.id"
                + " JOIN personas as p ON p.id = pa.persona_id"
                + " JOIN examiners as e ON e.id = p.examiner_id"
                + " WHERE LOWER(accounts.account_unique_identifier) LIKE LOWER('%" + partialName + "%')"
                + " AND p.status_id != " + Persona.PersonaStatus.DELETED.getStatusId()
                + " GROUP BY p.id";

        PersonaQueryCallback queryCallback = new PersonaQueryCallback();
        CentralRepository cr = CentralRepository.getInstance();
        if (cr != null) {
            cr.executeSelectSQL(queryClause, queryCallback);
        }

        return queryCallback.getPersonas();
    }
    
    /**
     * Creates an alias for the Persona.
     *
     * @param alias Alias name.
     * @param justification Reason for assigning the alias, may be null.
     * @param confidence Confidence level.
     *
     * @return PersonaAlias
     * @throws CentralRepoException If there is an error in creating the alias.
     */
    public PersonaAlias addAlias(String alias, String justification, Persona.Confidence confidence) throws CentralRepoException {
        return PersonaAlias.addPersonaAlias(this, alias, justification, confidence);
    }
    
    /**
     * Removes the given alias
     *
     * @param alias alias to remove
     *
     * @throws CentralRepoException If there is an error in querying the
     * Personas table.
     */
    public void removeAlias(PersonaAlias alias) throws CentralRepoException {
        PersonaAlias.removePersonaAlias(alias);
    }

    /**
     * Gets all aliases for the persona.
     *
     * @return A collection of aliases, may be empty.
     *
     * @throws CentralRepoException If there is an error in retrieving aliases.
     */
    public Collection<PersonaAlias> getAliases() throws CentralRepoException {
        return PersonaAlias.getPersonaAliases(this.getId());
    }

    /**
     * Adds specified metadata to the persona.
     *
     * @param name Metadata name.
     * @param value Metadata value.
     * @param justification Reason for adding the metadata, may be null.
     * @param confidence Confidence level.
     *
     * @return PersonaMetadata
     * @throws CentralRepoException If there is an error in adding metadata.
     */
    public PersonaMetadata addMetadata(String name, String value, String justification, Persona.Confidence confidence) throws CentralRepoException {
        return PersonaMetadata.addPersonaMetadata(this.getId(), name, value, justification, confidence);
    }
    
    /**
     * Removes the given metadata from this persona
     *
     * @param metadata metadata to remove
     *
     * @throws CentralRepoException If there is an error in querying the
     * Personas table.
     */
    public void removeMetadata(PersonaMetadata metadata) throws CentralRepoException {
        PersonaMetadata.removePersonaMetadata(metadata);
    }

    /**
     * Gets all metadata for the persona.
     *
     * @return A collection of metadata, may be empty.
     *
     * @throws CentralRepoException If there is an error in retrieving aliases.
     */
    public Collection<PersonaMetadata> getMetadata() throws CentralRepoException {
        return PersonaMetadata.getPersonaMetadata(this.getId());
    }

    /**
     * Gets all the Accounts for the Persona.
     *
     * @return Collection of PersonaAccounts, may be empty.
     *
     * @throws CentralRepoException If there is an error in getting the
     * persona_account.
     */
    public Collection<PersonaAccount> getPersonaAccounts() throws CentralRepoException {
        return PersonaAccount.getPersonaAccountsForPersona(this.getId());
    }
 
    /**
     * Callback to process a query that gets cases for account instances of an
     * account
     */
    private static class CaseForAccountInstanceQueryCallback implements CentralRepositoryDbQueryCallback {

        Collection<CorrelationCase> correlationCases = new ArrayList<>();

        @Override
        public void process(ResultSet resultSet) throws CentralRepoException, SQLException {

            while (resultSet.next()) {
                // get Case for case_id
                CorrelationCase correlationCase = CentralRepository.getInstance().getCaseById(resultSet.getInt("case_id"));
                correlationCases.add(correlationCase);
            }
        }

        Collection<CorrelationCase> getCases() {
            return Collections.unmodifiableCollection(correlationCases);
        }
    };

    /**
     * Gets a list of cases that the persona appears in.
     *
     * @return Collection of cases that the persona appears in, may be empty.
     * @throws CentralRepoException If there is an error in getting the cases
     * from the database.
     */
    public Collection<CorrelationCase> getCases() throws CentralRepoException {

        Collection<CorrelationCase> casesForPersona = new ArrayList<>();

        // get all accounts for this persona
        Collection<CentralRepoAccount> accounts = PersonaAccount.getAccountsForPersona(this.getId());
        for (CentralRepoAccount account : accounts) {
            int corrTypeId = account.getAccountType().getCorrelationTypeId();
            CorrelationAttributeInstance.Type correlationType = CentralRepository.getInstance().getCorrelationTypeById(corrTypeId);

            String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(correlationType);
            String querySql = "SELECT DISTINCT case_id FROM " + tableName
                    + " WHERE account_id = " + account.getId();

            CaseForAccountInstanceQueryCallback queryCallback = new CaseForAccountInstanceQueryCallback();
            CentralRepository.getInstance().executeSelectSQL(querySql, queryCallback);

            // Add any cases that aren't already on the list.
            for (CorrelationCase corrCase : queryCallback.getCases()) {
                if (!casesForPersona.stream().anyMatch(p -> p.getCaseUUID().equalsIgnoreCase(corrCase.getCaseUUID()))) {
                    casesForPersona.add(corrCase);
                }
            }
        }

        return casesForPersona;
    }

    /**
     * Callback to process a query that gets data source for account instances
     * of an account
     */
    private static class DatasourceForAccountInstanceQueryCallback implements CentralRepositoryDbQueryCallback {

        Collection<CorrelationDataSource> correlationDataSources = new ArrayList<>();

        @Override
        public void process(ResultSet resultSet) throws CentralRepoException, SQLException {

            while (resultSet.next()) {
                // get Case for case_id

                CorrelationCase correlationCase = CentralRepository.getInstance().getCaseById(resultSet.getInt("case_id"));
                CorrelationDataSource correlationDatasource = CentralRepository.getInstance().getDataSourceById(correlationCase, resultSet.getInt("data_source_id"));

                // Add data source to list if not already on it.
                if (!correlationDataSources.stream().anyMatch(p -> Objects.equals(p.getDataSourceObjectID(), correlationDatasource.getDataSourceObjectID()))) {
                    correlationDataSources.add(correlationDatasource);
                }
            }
        }

        Collection<CorrelationDataSource> getDataSources() {
            return Collections.unmodifiableCollection(correlationDataSources);
        }
    };

    /**
     * Gets all data sources that the persona appears in.
     *
     * @return Collection of data sources that the persona appears in, may be
     * empty.
     *
     * @throws CentralRepoException
     */
    public Collection<CorrelationDataSource> getDataSources() throws CentralRepoException {
        Collection<CorrelationDataSource> correlationDataSources = new ArrayList<>();

        Collection<CentralRepoAccount> accounts = PersonaAccount.getAccountsForPersona(this.getId());
        for (CentralRepoAccount account : accounts) {
            int corrTypeId = account.getAccountType().getCorrelationTypeId();
            CorrelationAttributeInstance.Type correlationType = CentralRepository.getInstance().getCorrelationTypeById(corrTypeId);

            String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(correlationType);
            String querySql = "SELECT case_id, data_source_id FROM " + tableName
                    + " WHERE account_id = " + account.getId();

            DatasourceForAccountInstanceQueryCallback queryCallback = new DatasourceForAccountInstanceQueryCallback();
            CentralRepository.getInstance().executeSelectSQL(querySql, queryCallback);

            // Add any data sources that aren't already on the list.
            for (CorrelationDataSource correlationDatasource : queryCallback.getDataSources()) {
                if (!correlationDataSources.stream().anyMatch(p -> Objects.equals(p.getDataSourceObjectID(), correlationDatasource.getDataSourceObjectID()))) {
                    correlationDataSources.add(correlationDatasource);
                }
            }
        }

        return correlationDataSources;
    }

    /**
     * Callback to process a query that gets Personas for a case/datasource.
     */
    private static class PersonaFromAccountInstanceQueryCallback implements CentralRepositoryDbQueryCallback {

        Collection<Persona> personasList = new ArrayList<>();

        @Override
        public void process(ResultSet resultSet) throws CentralRepoException, SQLException {

            while (resultSet.next()) {

                // examiner that created the persona
                CentralRepoExaminer personaExaminer = new CentralRepoExaminer(
                        resultSet.getInt("persona_examiner_id"),
                        resultSet.getString("persona_examiner_login_name"));

                // create persona
                PersonaStatus status = PersonaStatus.fromId(resultSet.getInt("status_id"));
                Persona persona = new Persona(
                        resultSet.getInt("persona_id"),
                        resultSet.getString("uuid"),
                        resultSet.getString("name"),
                        resultSet.getString("comment"),
                        Long.parseLong(resultSet.getString("created_date")),
                        Long.parseLong(resultSet.getString("modified_date")),
                        status,
                        personaExaminer
                );

                personasList.add(persona);
            }
        }

        Collection<Persona> getPersonasList() {
            return Collections.unmodifiableCollection(personasList);
        }
    };

    /**
     * Returns a query string for selecting personas for a case/datasource from
     * the X_instance table for the given account type.
     *
     * @param crAccountType Account type to generate the query string for.
     * @return Query substring.
     * @throws CentralRepoException
     */
    private static String getPersonaFromInstanceTableQueryTemplate(CentralRepoAccount.CentralRepoAccountType crAccountType) throws CentralRepoException {

        int corrTypeId = crAccountType.getCorrelationTypeId();
        CorrelationAttributeInstance.Type correlationType = CentralRepository.getInstance().getCorrelationTypeById(corrTypeId);

        String instanceTableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(correlationType);
        return "SELECT " + instanceTableName + ".account_id, case_id, data_source_id, "
                + " personas.id as persona_id, personas.uuid, personas.name, personas.comment, personas.created_date, personas.modified_date, personas.status_id, "
                + " personas.examiner_id as persona_examiner_id, persona_examiner.login_name as persona_examiner_login_name, persona_examiner.display_name as persona_examiner_display_name "
                + " FROM " + instanceTableName
                + " JOIN persona_accounts as pa on pa.account_id = " + instanceTableName + ".account_id"
                + " JOIN personas as personas on personas.id = pa.persona_id"
                + " JOIN examiners as persona_examiner ON persona_examiner.id = personas.examiner_id ";

    }

    /**
     * Get all the persona for a given case.
     *
     * @param correlationCase Case to look the persona in.
     *
     * @return Collection of personas, may be empty.
     * @throws CentralRepoException
     */
    public static Collection<Persona> getPersonasForCase(CorrelationCase correlationCase) throws CentralRepoException {
        Collection<Persona> personaList = new ArrayList<>();

        Collection<CentralRepoAccount.CentralRepoAccountType> accountTypes = CentralRepository.getInstance().getAllAccountTypes();
        for (CentralRepoAccount.CentralRepoAccountType crAccountType : accountTypes) {

            String querySql = getPersonaFromInstanceTableQueryTemplate(crAccountType)
                    + " WHERE case_id = " + correlationCase.getID()
                    + "AND personas.status_id != " + Persona.PersonaStatus.DELETED.getStatusId();

            PersonaFromAccountInstanceQueryCallback queryCallback = new PersonaFromAccountInstanceQueryCallback();
            CentralRepository.getInstance().executeSelectSQL(querySql, queryCallback);

            // Add persona that aren't already on the list.
            for (Persona persona : queryCallback.getPersonasList()) {
                if (!personaList.stream().anyMatch(p -> Objects.equals(p.getUuidStr(), persona.getUuidStr()))) {
                    personaList.add(persona);
                }
            }

        }
        return personaList;
    }

    /**
     * Get all the persona for a given data source.
     *
     * @param dataSource Data source to look the persona in.
     *
     * @return Collection of personas, may be empty.
     * @throws CentralRepoException
     */
    public static Collection<Persona> getPersonasForDataSource(CorrelationDataSource dataSource) throws CentralRepoException {
        Collection<Persona> personaList = new ArrayList<>();

        Collection<CentralRepoAccount.CentralRepoAccountType> accountTypes = CentralRepository.getInstance().getAllAccountTypes();
        for (CentralRepoAccount.CentralRepoAccountType crAccountType : accountTypes) {

            String querySql = getPersonaFromInstanceTableQueryTemplate(crAccountType)
                    + " WHERE data_source_id = " + dataSource.getID()
                    + "AND personas.status_id != " + Persona.PersonaStatus.DELETED.getStatusId();

            PersonaFromAccountInstanceQueryCallback queryCallback = new PersonaFromAccountInstanceQueryCallback();
            CentralRepository.getInstance().executeSelectSQL(querySql, queryCallback);

            // Add persona that aren't already on the list.
            for (Persona persona : queryCallback.getPersonasList()) {
                if (!personaList.stream().anyMatch(p -> Objects.equals(p.getUuidStr(), persona.getUuidStr()))) {
                    personaList.add(persona);
                }
            }

        }
        return personaList;
    }
}

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
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.datamodel.Examiner;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount.CentralRepoAccountType;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona.PersonaStatus;
import org.sleuthkit.datamodel.SleuthkitCase;




/**
 *  Provides APIs to create, modify and retrieve Persona.
 * 
 */
public class PersonaHelper {
    
    // Persona name to use if no name is specified.
    private static final String DEFAULT_PERSONA_NAME = "NoName";
    
   /**
     * Empty private constructor
     */
    private PersonaHelper() {
        
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
     * @return PersonaAccount
     * @throws CentralRepoException If there is an error creating the Persona.
     */
    public static PersonaAccount createPersonaForAccount(String personaName, String comment, PersonaStatus status, CentralRepoAccount account, String justification, Persona.Confidence confidence) throws CentralRepoException {
        Persona persona = createPersona( personaName,  comment,  status);   
        return addAccountToPersona( persona,  account,  justification,  confidence);
    }
    
    
    /**
     * Inserts a row in the Persona tables.
     *
     * @param name Persona name, may be null - default name is used in that case.
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
        Examiner examiner = CentralRepository.getInstance().getCurrentCentralRepoExaminer();
        
        Instant instant = Instant.now();
        Long timeStampMillis = instant.toEpochMilli();
        String insertClause = " INTO personas (uuid, comment, name, created_date, modified_date, status_id, examiner_id ) "
					+ "VALUES ( '" + uuidStr + "', "
					+ "'" + ((StringUtils.isBlank(comment) ?  "" : SleuthkitCase.escapeSingleQuotes(comment)))  + "',"
                                        + "'" + ((StringUtils.isBlank(name) ?  DEFAULT_PERSONA_NAME : SleuthkitCase.escapeSingleQuotes(name)))  + "',"
                                        + timeStampMillis.toString() + ","
                                        + timeStampMillis.toString() + ","
                                        + status.getStatusId() + ","
                                        + examiner.getId()
                                        + ")";
        		
        CentralRepository.getInstance().executeInsertSQL(insertClause);
        return getPersonaByUUID(uuidStr); 
    }
   
    /**
     * Callback to process a Persona query from the persona table.
     */
    private static class PersonaQueryCallback implements CentralRepositoryDbQueryCallback {

        private Persona persona = null;

        @Override
        public void process(ResultSet rs) throws SQLException {

            while (rs.next()) {
                Examiner examiner = new Examiner(
                        rs.getInt("examiner_id"),
                        rs.getString("login_name"),
                        rs.getString("display_name"));

                PersonaStatus status = PersonaStatus.fromId(rs.getInt("status_id"));
                persona = new Persona(
                        rs.getInt("id"),
                        rs.getString("uuid"),
                        rs.getString("name"),
                        rs.getString("comment"),
                        Long.parseLong(rs.getString("created_date")),
                        Long.parseLong(rs.getString("modified_date")),
                        status,
                        examiner
                );
            }
        }

        Persona getPersona() {
            return persona;
        }
    };
    
    /**
     * Gets the row from the Personas table with the given UUID, 
     * creates and returns the Persona from that data.
     * 
     * @param uuid Persona UUID to match.
     * @return Persona matching the given UUID, may be null if no match is found.
     * 
     * @throws CentralRepoException If there is an error in querying the Personas table.
     */
    private static Persona getPersonaByUUID(String uuid) throws CentralRepoException {
        
        String queryClause = "SELECT p.id, p.uuid, p.name, p.comment, p.created_date, p.modified_date, p.status_id, p.examiner_id, e.login_name, e.display_name "
				+ "FROM personas as p "
				+ "INNER JOIN examiners as e ON e.id = p.examiner_id "
                                + "WHERE p.uuid = '" + uuid + "'";
        
        PersonaQueryCallback queryCallback =  new PersonaQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);
        
        return queryCallback.getPersona();
    }
    
    /**
     * Gets the row from the Personas table with the given id, 
     * creates and returns the Persona from that data.
     * 
     * @param id Persona id to match.
     * @return Persona matching the given UUID, may be null if no match is found.
     * 
     * @throws CentralRepoException If there is an error in querying the Personas table.
     */
    
    public static Persona getPersonaById(long id) throws CentralRepoException {
        
        String queryClause = "SELECT p.id, p.uuid, p.name, p.comment, p.created_date, p.modified_date, p.status_id, p.examiner_id, e.login_name, e.display_name "
				+ "FROM personas as p "
				+ "INNER JOIN examiners as e ON e.id = p.examiner_id "
                                + "WHERE p.id = " + id;
        
        PersonaQueryCallback queryCallback =  new PersonaQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);
        
        return queryCallback.getPersona();
    }
    
    /**
     * Associates an account with a persona by creating a row in the PersonaAccounts table.
     * 
     * @param persona   Persona to add the account to.
     * @param account   Account to add to persona.
     * @param justification Reason for adding the account to persona, may be null.
     * @param confidence Confidence level.
     * 
     * @return PersonaAccount
     * @throws CentralRepoException If there is an error. 
     */
    public static PersonaAccount addAccountToPersona(Persona persona, CentralRepoAccount account, String justification, Persona.Confidence confidence) throws CentralRepoException {
        
        Examiner examiner = CentralRepository.getInstance().getCurrentCentralRepoExaminer();
        
        Instant instant = Instant.now();
        Long timeStampMillis = instant.toEpochMilli();
        String insertClause = " INTO persona_accounts (persona_id, account_id, justification, confidence_id, date_added, examiner_id ) "
					+ "VALUES ( " 
                                            + persona.getId() + ", "
                                            + account.getAccountId() + ", "
                                            + "'" + ((StringUtils.isBlank(justification) ?  "" : SleuthkitCase.escapeSingleQuotes(justification)))  + "', "
                                            + confidence.getLevelId() + ", "
                                            + timeStampMillis.toString() + ", "
                                            + examiner.getId()
                                        + ")";
        
        CentralRepository.getInstance().executeInsertSQL(insertClause);
        return new PersonaAccount(persona, account, justification, confidence, timeStampMillis, examiner);
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
    public static PersonaAlias addPersonaAlias(Persona persona,  String alias, String justification, Persona.Confidence confidence) throws CentralRepoException {
        
        Examiner examiner = CentralRepository.getInstance().getCurrentCentralRepoExaminer();
        
        Instant instant = Instant.now();
        Long timeStampMillis = instant.toEpochMilli();

        String insertClause = " INTO persona_alias (persona_id, alias, justification, confidence_id, date_added, examiner_id ) "
					+ "VALUES ( " 
                                            + persona.getId() + ", "
                                            + "'" + alias +  "', "
                                            + "'" + ((StringUtils.isBlank(justification) ?  "" : SleuthkitCase.escapeSingleQuotes(justification)))  + "', "
                                            + confidence.getLevelId() + ", "
                                            + timeStampMillis.toString() + ", "
                                            + examiner.getId()
                                        + ")";
        
        CentralRepository.getInstance().executeInsertSQL(insertClause);
        return new PersonaAlias(persona.getId(), alias, justification, confidence, timeStampMillis, examiner); 
    }
    
    /**
     * Adds specified metadata to the given persona.
     * 
     * @param persona Persona to add metadata to.
     * @param name    Metadata name.
     * @param value   Metadata value.
     * @param justification Reason for adding the metadata, may be null.
     * @param confidence Confidence level.
     * 
     * @return PersonaMetadata
     * @throws CentralRepoException If there is an error in adding metadata.
     */ 
    public static PersonaMetadata addPersonaMetadata(Persona persona,  String name, String value, String justification, Persona.Confidence confidence) throws CentralRepoException {
        
        Examiner examiner = CentralRepository.getInstance().getCurrentCentralRepoExaminer();
        
        Instant instant = Instant.now();
        Long timeStampMillis = instant.toEpochMilli();

        String insertClause = " INTO persona_metadata (persona_id, name, value, justification, confidence_id, date_added, examiner_id ) "
					+ "VALUES ( " 
                                            + persona.getId() + ", "
                                            + "'" + name +  "', "
                                            + "'" + value +  "', "
                                            + "'" + ((StringUtils.isBlank(justification) ?  "" : SleuthkitCase.escapeSingleQuotes(justification)))  + "', "
                                            + confidence.getLevelId() + ", "
                                            + timeStampMillis.toString() + ", "
                                            + examiner.getId()
                                        + ")";
        
        CentralRepository.getInstance().executeInsertSQL(insertClause);
        return new PersonaMetadata(persona.getId(), name, value, justification, confidence, timeStampMillis, examiner);
    }
    
    
    /**
     * Callback to process a Persona aliases query.
     */
    private static class PersonaAliasesQueryCallback implements CentralRepositoryDbQueryCallback {

        Collection<PersonaAlias> personaAliases = new ArrayList<>();
        
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
                        Persona.Confidence.fromId( rs.getInt("confidence_id")),
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
        
        PersonaAliasesQueryCallback queryCallback =  new PersonaAliasesQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);
        
        return queryCallback.getAliases();
        
    }
     
    /**
     * Callback to process a Persona metadata query.
     */
    private static class PersonaMetadataQueryCallback implements CentralRepositoryDbQueryCallback {

        Collection<PersonaMetadata> personaMetadataList = new ArrayList<>();
        
        @Override
        public void process(ResultSet rs) throws SQLException {

            while (rs.next()) {
                Examiner examiner = new Examiner(
                        rs.getInt("examiner_id"),
                        rs.getString("login_name"),
                        rs.getString("display_name"));

                PersonaMetadata metaData = new PersonaMetadata(
                        rs.getLong("persona_id"), 
                        rs.getString("name"),
                        rs.getString("value"),
                        rs.getString("justification"),
                        Persona.Confidence.fromId( rs.getInt("confidence_id")),
                        Long.parseLong(rs.getString("date_added")),
                        examiner);
                          
                personaMetadataList.add(metaData);
            }
        }

        Collection<PersonaMetadata> getMetadataList() {
            return Collections.unmodifiableCollection(personaMetadataList);
        }
    };
    
    /**
     * Gets all metadata for the persona with specified id.
     * 
     * @param personaId Id of the persona for which to get the metadata.
     * @return A collection of metadata, may be empty.
     * 
     * @throws CentralRepoException If there is an error in retrieving aliases.
     */
    public static Collection<PersonaMetadata> getPersonaMetadata(long personaId) throws CentralRepoException {
         String queryClause = "SELECT pmd.id, pmd.persona_id, pmd.name, pmd.value, pmd.justification, pmd.confidence_id, pmd.date_added, pmd.examiner_id, e.login_name, e.display_name "
				+ "FROM persona_metadata as pmd "
				+ "INNER JOIN examiners as e ON e.id = pmd.examiner_id ";
        
        PersonaMetadataQueryCallback queryCallback =  new PersonaMetadataQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);
        
        return queryCallback.getMetadataList();
        
    }
     
   
     /**
     * Callback to process a Persona Accounts query.
     */
    private static class PersonaAccountsQueryCallback implements CentralRepositoryDbQueryCallback {

        Collection<PersonaAccount> personaAccountsList = new ArrayList<>();
        
        @Override
        public void process(ResultSet rs) throws CentralRepoException, SQLException {

            while (rs.next()) {
                // examiner that created the persona/account association
                Examiner paExaminer = new Examiner(
                        rs.getInt("pa_examiner_id"),
                        rs.getString("pa_examiner_login_name"),
                        rs.getString("pa_examiner_display_name"));

                // examiner that created the persona
                Examiner personaExaminer = new Examiner(
                        rs.getInt("persona_examiner_id"),
                        rs.getString("persona_examiner_login_name"),
                        rs.getString("persona_examiner_display_name"));
                
              
                // create persona
                PersonaStatus status = PersonaStatus.fromId(rs.getInt("status_id"));
                Persona persona = new Persona(
                        rs.getInt("persona_id"), 
                        rs.getString("uuid"),
                        rs.getString("name"),
                        rs.getString("comment"),
                        Long.parseLong(rs.getString("created_date")),
                        Long.parseLong(rs.getString("modified_date")),
                        status,
                        personaExaminer        
                );
                        
               
                // create account
                CentralRepoAccountType crAccountType = CentralRepository.getInstance().getAccountTypeByName(rs.getString("type_name"));
                CentralRepoAccount account =   new CentralRepoAccount(
                        rs.getInt("account_id"), 
                        crAccountType, 
                        rs.getString("account_unique_identifier"));
                
                // create persona account
                PersonaAccount personaAccount = new PersonaAccount(persona, account, 
                        rs.getString("justification"),
                        Persona.Confidence.fromId( rs.getInt("confidence_id")),
                        Long.parseLong(rs.getString("date_added")), 
                        paExaminer);
                
                personaAccountsList.add(personaAccount);
            }
        }

        Collection<PersonaAccount> getPersonaAccountsList() {
            return Collections.unmodifiableCollection(personaAccountsList);
        }
    };
    
    // Query clause  to select from persona_accounts table to create PersonaAccount(s)
    private static final String PERSONA_ACCOUNTS_QUERY_CALUSE = "SELECT justification, confidence_id, date_added, persona_accounts.examiner_id as pa_examiner_id, pa_examiner.login_name as pa_examiner_login_name, pa_examiner.display_name as pa_examiner_display_name,"
                                + " personas.id as persona_id, personas.uuid, personas.name, personas.comment, personas.created_date, personas.modified_date, personas.status_id, "
                                + " personas.examiner_id as persona_examiner_id, persona_examiner.login_name as persona_examiner_login_name, persona_examiner.display_name as persona_examiner_display_name, "
                                + " accounts.id as account_id, account_type_id, account_unique_identifier,"
                                + " account_types.type_name as type_name " 
                                + " FROM persona_accounts as persona_accounts "
				+ " JOIN personas as personas on persona_accounts.persona_id = personas.id "
                                + " JOIN accounts as accounts on persona_accounts.account_id = accounts.id "
                                + " JOIN account_types as account_types on accounts.account_type_id = account_types.id "
				+ " JOIN examiners as pa_examiner ON pa_examiner.id = persona_accounts.examiner_id "
                                + " JOIN examiners as persona_examiner ON persona_examiner.id = personas.examiner_id ";
                               
    
    /**
     * Gets all the Accounts for the specified Persona.
     * 
     * @param personaId Id of persona for which to get the accounts for.
     * @return Collection of PersonaAccounts, may be empty.
     * 
     * @throws CentralRepoException If there is an error in getting the persona_account.
     */
    public static Collection<PersonaAccount> getPersonaAccountsForPersona(long personaId) throws CentralRepoException {
         String queryClause =   PERSONA_ACCOUNTS_QUERY_CALUSE
                                + " WHERE persona_accounts.persona_id = " + personaId ;
        
        PersonaAccountsQueryCallback queryCallback =  new PersonaAccountsQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);
        
        return queryCallback.getPersonaAccountsList();
        
    }
     
    /**
     * Gets all the Persona for the specified Account.
     * 
     * @param accountId Id of account for which to get the Personas for.
     * @return Collection of PersonaAccounts. may be empty.
     * 
     * @throws CentralRepoException If there is an error in getting the persona_account.
     */
    public static Collection<PersonaAccount> getPersonaAccountsForAccount(long accountId) throws CentralRepoException {
         String queryClause =   PERSONA_ACCOUNTS_QUERY_CALUSE
                                + " WHERE persona_accounts.account_id = " + accountId ;
        
        PersonaAccountsQueryCallback queryCallback =  new PersonaAccountsQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);
        
        return queryCallback.getPersonaAccountsList();
    }
    
// TBD:
//    public Collection<CorrelationCase> getCasesForPersona(Persona persona) {
//    }
//    public Collection<CorrelationDataSource> getDataSourcesForPersona(Persona persona) {
//    }
//    public Collection<Persona> getPersonasForCase(CorrelationCase case) {
//    }
//    public Collection<Persona>  getPersonaForDataSource() {
//    }

     
}

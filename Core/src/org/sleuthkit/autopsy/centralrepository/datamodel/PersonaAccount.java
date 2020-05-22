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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * This class represents an association between a Persona and an Account.
 * 
 * A Persona has at least one, possibly more, accounts associated with it.
 * 
 * 
 */
public class PersonaAccount {

    private final Persona persona;
    private final CentralRepoAccount account;
    private final String justification;
    private final Persona.Confidence confidence;
    private final long dateAdded;
    private final CentralRepoExaminer examiner;

    public PersonaAccount(Persona persona, CentralRepoAccount account, String justification, Persona.Confidence confidence, long dateAdded, CentralRepoExaminer examiner) {
        this.persona = persona;
        this.account = account;
        this.justification = justification;
        this.confidence = confidence;
        this.dateAdded = dateAdded;
        this.examiner = examiner;
    }

    public Persona getPersona() {
        return persona;
    }

    public CentralRepoAccount getAccount() {
        return account;
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

    public CentralRepoExaminer getExaminer() {
        return examiner;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + Objects.hashCode(this.persona);
        hash = 83 * hash + Objects.hashCode(this.account);
        hash = 83 * hash + (int) (this.dateAdded ^ (this.dateAdded >>> 32));
        hash = 83 * hash + Objects.hashCode(this.examiner);
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
        final PersonaAccount other = (PersonaAccount) obj;
        if (this.dateAdded != other.getDateAdded()) {
            return false;
        }
        if (!Objects.equals(this.persona, other.getPersona())) {
            return false;
        }
        if (!Objects.equals(this.account, other.getAccount())) {
            return false;
        }
        return Objects.equals(this.examiner, other.getExaminer());
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
                CentralRepoExaminer paExaminer = new CentralRepoExaminer(
                        rs.getInt("pa_examiner_id"),
                        rs.getString("pa_examiner_login_name"));

                // examiner that created the persona
                CentralRepoExaminer personaExaminer = new CentralRepoExaminer(
                        rs.getInt("persona_examiner_id"),
                        rs.getString("persona_examiner_login_name"));

                // create persona
                Persona.PersonaStatus status = Persona.PersonaStatus.fromId(rs.getInt("status_id"));
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
                CentralRepoAccount.CentralRepoAccountType crAccountType = CentralRepository.getInstance().getAccountTypeByName(rs.getString("type_name"));
                CentralRepoAccount account = new CentralRepoAccount(
                        rs.getInt("account_id"),
                        crAccountType,
                        rs.getString("account_unique_identifier"));

                // create persona account
                PersonaAccount personaAccount = new PersonaAccount(persona, account,
                        rs.getString("justification"),
                        Persona.Confidence.fromId(rs.getInt("confidence_id")),
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
     * @throws CentralRepoException If there is an error in getting the
     * persona_account.
     */
    static Collection<PersonaAccount> getPersonaAccountsForPersona(long personaId) throws CentralRepoException {
        String queryClause = PERSONA_ACCOUNTS_QUERY_CALUSE
                + " WHERE persona_accounts.persona_id = " + personaId;

        PersonaAccountsQueryCallback queryCallback = new PersonaAccountsQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);

        return queryCallback.getPersonaAccountsList();
    }
    
    /**
     * Gets all the Persona for the specified Account.
     *
     * @param accountId Id of account for which to get the Personas for.
     * @return Collection of PersonaAccounts. may be empty.
     *
     * @throws CentralRepoException If there is an error in getting the
     * persona_account.
     */
    public static Collection<PersonaAccount> getPersonaAccountsForAccount(long accountId) throws CentralRepoException {
        String queryClause = PERSONA_ACCOUNTS_QUERY_CALUSE
                + " WHERE persona_accounts.account_id = " + accountId;

        PersonaAccountsQueryCallback queryCallback = new PersonaAccountsQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);

        return queryCallback.getPersonaAccountsList();
    }
   
    /**
     * Gets all the Persona associated with all the accounts matching the given
     * account identifier substring.
     *
     * @param accountIdentifierSubstring Account identifier substring to search
     * for.
     * @return Collection of PersonaAccounts. may be empty.
     *
     * @throws CentralRepoException If there is an error in getting the
     * persona_account.
     */
    public static Collection<PersonaAccount> getPersonaAccountsForIdentifierLike(String accountIdentifierSubstring) throws CentralRepoException {
        String queryClause = PERSONA_ACCOUNTS_QUERY_CALUSE
                + " WHERE LOWER(accounts.account_unique_identifier) LIKE LOWER('%" + accountIdentifierSubstring + "%')";

        PersonaAccountsQueryCallback queryCallback = new PersonaAccountsQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);

        return queryCallback.getPersonaAccountsList();
    }
    
    /**
     * Callback to process a query that gets all accounts belonging to a
     * persona.
     */
    private static class AccountsForPersonaQueryCallback implements CentralRepositoryDbQueryCallback {

        Collection<CentralRepoAccount> accountsList = new ArrayList<>();

        @Override
        public void process(ResultSet rs) throws CentralRepoException, SQLException {

            while (rs.next()) {

                // create account
                CentralRepoAccount.CentralRepoAccountType crAccountType = CentralRepository.getInstance().getAccountTypeByName(rs.getString("type_name"));
                CentralRepoAccount account = new CentralRepoAccount(
                        rs.getInt("account_id"),
                        crAccountType,
                        rs.getString("account_unique_identifier"));

                accountsList.add(account);
            }
        }

        Collection<CentralRepoAccount> getAccountsList() {
            return Collections.unmodifiableCollection(accountsList);
        }
    };

    /**
     * Get all accounts associated with a persona.
     *
     * @param personaId Id of the persona to look for.
     *
     * @return Collection of all accounts associated with the given persona, may
     * be empty.
     * @throws CentralRepoException If there is an error in getting the accounts.
     */
    static Collection<CentralRepoAccount> getAccountsForPersona(long personaId) throws CentralRepoException {
        String queryClause = "SELECT account_id,  "
                + " accounts.account_type_id as account_type_id, accounts.account_unique_identifier as account_unique_identifier,"
                + " account_types.type_name as type_name "
                + " FROM persona_accounts "
                + " JOIN accounts as accounts on persona_accounts.account_id = accounts.id "
                + " JOIN account_types as account_types on accounts.account_type_id = account_types.id "
                + " WHERE persona_accounts.persona_id = " + personaId;

        AccountsForPersonaQueryCallback queryCallback = new AccountsForPersonaQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);

        return queryCallback.getAccountsList();
    }
}

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
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.InvalidAccountIDException;

/**
 * This class abstracts an Account as stored in the CR database.
 */
public final class CentralRepoAccount {

    // primary key in the Accounts table in CR database
    private final long accountId;

    private final CentralRepoAccountType accountType;

    // type specific unique account identifier
    // Stores what is in the DB which should have been normalized before insertion.
    private final String typeSpecificIdentifier;

    /**
     * Encapsulates a central repo account type and the correlation type that it
     * maps to.
     */
    public static final class CentralRepoAccountType {

        // id is the primary key in the account_types table
        private final int accountTypeId;
        private final Account.Type acctType;
        private final int correlationTypeId;

        CentralRepoAccountType(int acctTypeID, Account.Type acctType, int correlationTypeId) {
            this.acctType = acctType;
            this.correlationTypeId = correlationTypeId;
            this.accountTypeId = acctTypeID;
        }

        /**
         * @return the acctType
         */
        public Account.Type getAcctType() {
            return acctType;
        }

        public int getCorrelationTypeId() {
            return this.correlationTypeId;
        }

        public int getAccountTypeId() {
            return this.accountTypeId;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 29 * hash + this.accountTypeId;
            hash = 29 * hash + Objects.hashCode(this.acctType);
            hash = 29 * hash + this.correlationTypeId;
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
            final CentralRepoAccountType other = (CentralRepoAccountType) obj;
            if (this.accountTypeId != other.getAccountTypeId()) {
                return false;
            }
            if (this.correlationTypeId != other.getCorrelationTypeId()) {
                return false;
            }
            return Objects.equals(this.acctType, other.getAcctType());
        }

    }

    CentralRepoAccount(long accountId, CentralRepoAccountType accountType, String typeSpecificIdentifier) {
        this.accountId = accountId;
        this.accountType = accountType;
        this.typeSpecificIdentifier = typeSpecificIdentifier;
    }

    /**
     * Gets unique identifier (assigned by a provider) for the account. Example
     * includes an email address, a phone number, or a website username.
     * 
     * This is the normalized for of the ID.
     *
     * @return type specific account id.
     */
    public String getIdentifier() {
        return this.typeSpecificIdentifier;
    }

    /**
     * Gets the account type
     *
     * @return account type
     */
    public CentralRepoAccountType getAccountType() {
        return this.accountType;
    }

    /**
     * Gets the unique row id for this account in the database.
     *
     * @return unique row id.
     */
    public long getId() {
        return this.accountId;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 43 * hash + (int) (this.accountId ^ (this.accountId >>> 32));
        hash = 43 * hash + (this.accountType != null ? this.accountType.hashCode() : 0);
        hash = 43 * hash + (this.typeSpecificIdentifier != null ? this.typeSpecificIdentifier.hashCode() : 0);
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
        final CentralRepoAccount other = (CentralRepoAccount) obj;
        if (this.accountId != other.getId()) {
            return false;
        }
        if (!Objects.equals(this.typeSpecificIdentifier, other.getIdentifier())) {
            return false;
        }
        return Objects.equals(this.accountType, other.getAccountType());
    }

    /**
     * Callback to process a query that gets accounts
     */
    private static class AccountsQueryCallback implements CentralRepositoryDbQueryCallback {

        Collection<CentralRepoAccount> accountsList = new ArrayList<>();

        @Override
        public void process(ResultSet rs) throws CentralRepoException, SQLException {

            while (rs.next()) {

                // create account
                Account.Type acctType = new Account.Type(rs.getString("type_name"), rs.getString("display_name"));
                CentralRepoAccountType crAccountType = new CentralRepoAccountType(rs.getInt("account_type_id"), acctType, rs.getInt("correlation_type_id"));

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

    private static final String ACCOUNTS_QUERY_CLAUSE
            = "SELECT accounts.id as account_id,  "
            + " accounts.account_type_id as account_type_id, accounts.account_unique_identifier as account_unique_identifier,"
            + " account_types.id as account_type_id, "
            + " account_types.type_name as type_name, account_types.display_name as display_name, account_types.correlation_type_id as correlation_type_id  "
            + " FROM accounts "
            + " JOIN account_types as account_types on accounts.account_type_id = account_types.id ";

    /**
     * Get all accounts with account identifier matching the given substring.
     *
     * @param accountIdentifierSubstring Account identifier substring to look
     * for.
     *
     * @return Collection of all accounts with identifier matching the given
     * substring, may be empty.
     *
     * @throws CentralRepoException If there is an error in getting the
     * accounts.
     */
    public static Collection<CentralRepoAccount> getAccountsWithIdentifierLike(String accountIdentifierSubstring) throws CentralRepoException {

        String queryClause = ACCOUNTS_QUERY_CLAUSE
                + " WHERE LOWER(accounts.account_unique_identifier) LIKE LOWER(?)";

        List<Object> params = new ArrayList<>();
        params.add("%" + accountIdentifierSubstring + "%");

        AccountsQueryCallback queryCallback = new AccountsQueryCallback();
        CentralRepository.getInstance().executeQuery(queryClause, params, queryCallback);

        return queryCallback.getAccountsList();
    }

    /**
     * Get all accounts with account identifier matching the given identifier.
     *
     * @param accountIdentifier Account identifier to look for.
     *
     * @return Collection of all accounts with identifier matching the given
     * identifier, may be empty.
     *
     * @throws CentralRepoException If there is an error in getting the
     * accounts.
     */
    public static Collection<CentralRepoAccount> getAccountsWithIdentifier(String accountIdentifier) throws InvalidAccountIDException, CentralRepoException {

        String normalizedAccountIdentifier = normalizeAccountIdentifier(accountIdentifier);
        String queryClause = ACCOUNTS_QUERY_CLAUSE
                + " WHERE LOWER(accounts.account_unique_identifier) = LOWER(?)";

        List<Object> params = new ArrayList<>();
        params.add(normalizedAccountIdentifier);

        AccountsQueryCallback queryCallback = new AccountsQueryCallback();
        CentralRepository.getInstance().executeQuery(queryClause, params, queryCallback);

        return queryCallback.getAccountsList();
    }

    /**
     * Get all central repo accounts.
     *
     * @return Collection of all accounts with identifier matching the given
     * identifier, may be empty.
     *
     * @throws CentralRepoException If there is an error in getting the
     * accounts.
     */
    public static Collection<CentralRepoAccount> getAllAccounts() throws CentralRepoException {

        String queryClause = ACCOUNTS_QUERY_CLAUSE;

        List<Object> params = new ArrayList<>(); // empty param list

        AccountsQueryCallback queryCallback = new AccountsQueryCallback();
        CentralRepository.getInstance().executeQuery(queryClause, params, queryCallback);

        return queryCallback.getAccountsList();
    }

    /**
     * Attempts to normalize an account identifier, after trying to guess the
     * account type.
     *
     * @param accountIdentifier Account identifier to be normalized.
     * @return normalized identifier
     *
     * @throws InvalidAccountIDException If the account identifier is not valid.
     */
    private static String normalizeAccountIdentifier(String accountIdentifier) throws InvalidAccountIDException {
        if (StringUtils.isEmpty(accountIdentifier)) {
            throw new InvalidAccountIDException("Account id is null or empty.");
        }

        String normalizedAccountIdentifier;
        try {
            if (CorrelationAttributeNormalizer.isValidPhoneNumber(accountIdentifier)) {
                normalizedAccountIdentifier = CorrelationAttributeNormalizer.normalizePhone(accountIdentifier);
            } else if (CorrelationAttributeNormalizer.isValidEmailAddress(accountIdentifier)) {
                normalizedAccountIdentifier = CorrelationAttributeNormalizer.normalizeEmail(accountIdentifier);
            } else {
                normalizedAccountIdentifier = accountIdentifier.toLowerCase().trim();
            }
        } catch (CorrelationAttributeNormalizationException ex) {
            throw new InvalidAccountIDException("Failed to normalize the account idenitier " + accountIdentifier, ex);
        }
        return normalizedAccountIdentifier;
    }
    
    /**
     * Normalizes an account identifier, based on the given account type.
     *
     * @param crAccountType Account type.
     * @param accountIdentifier Account identifier to be normalized.
     * @return Normalized identifier.
     *
     * @throws InvalidAccountIDException If the account identifier is invalid.
     */
    public static String normalizeAccountIdentifier(CentralRepoAccountType crAccountType, String accountIdentifier) throws InvalidAccountIDException {
       
        if (StringUtils.isBlank(accountIdentifier)) {
            throw new InvalidAccountIDException("Account identifier is null or empty.");
        }
        
        String normalizedAccountIdentifier;
        try {
            if (crAccountType.getAcctType().equals(Account.Type.PHONE)) {
                normalizedAccountIdentifier = CorrelationAttributeNormalizer.normalizePhone(accountIdentifier);
            } else if (crAccountType.getAcctType().equals(Account.Type.EMAIL)) {
                normalizedAccountIdentifier = CorrelationAttributeNormalizer.normalizeEmail(accountIdentifier);
            } else {
                // convert to lowercase
                normalizedAccountIdentifier = accountIdentifier.toLowerCase();
            }
        } catch (CorrelationAttributeNormalizationException ex) {
            throw new InvalidAccountIDException(String.format("Account id normaization failed, invalid account identifier %s", accountIdentifier), ex);
        }

        return normalizedAccountIdentifier;
    }
}

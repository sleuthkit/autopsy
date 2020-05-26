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
import org.sleuthkit.datamodel.Account;


/**
 * This class abstracts an Account as stored in the CR database.
 */
public final class CentralRepoAccount {
    
	// primary key in the Accounts table in CR database
	private final long accountId;

	private final CentralRepoAccountType accountType;
        
        // type specific unique account identifier
	private final String typeSpecificIdentifier;
        
    /**
     * Encapsulates a central repo account type and the correlation type
     * that it maps to.
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
    }
    
    public CentralRepoAccount(long accountId, CentralRepoAccountType accountType, String typeSpecificIdentifier) {
		this.accountId = accountId;
		this.accountType = accountType;
		this.typeSpecificIdentifier = typeSpecificIdentifier;
	}

	/**
	 * Gets unique identifier (assigned by a provider) for the account. Example
	 * includes an email address, a phone number, or a website username.
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
		if ((this.typeSpecificIdentifier == null) ? (other.getIdentifier() != null) : !this.typeSpecificIdentifier.equals(other.getIdentifier())) {
			return false;
		}
		return !(this.accountType != other.getAccountType() && (this.accountType == null || !this.accountType.equals(other.getAccountType())));
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
     * @param accountIdentifierSubstring Account identifier substring to look for.
     *
     * @return Collection of all accounts with identifier matching the given substring, may
     * be empty.
     * 
     * @throws CentralRepoException If there is an error in getting the accounts.
     */
    public static Collection<CentralRepoAccount> getAccountsWithIdentifierLike(String accountIdentifierSubstring) throws CentralRepoException {
       
        String queryClause = ACCOUNTS_QUERY_CLAUSE
                + " WHERE LOWER(accounts.account_unique_identifier) LIKE LOWER('%" + accountIdentifierSubstring + "%')";

        AccountsQueryCallback queryCallback = new AccountsQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);

        return queryCallback.getAccountsList();
    }
    
    /**
     * Get all accounts with account identifier matching the given identifier.
     *
     * @param accountIdentifier Account identifier to look for.
     *
     * @return Collection of all accounts with identifier matching the given identifier, may
     * be empty.
     * 
     * @throws CentralRepoException If there is an error in getting the accounts.
     */
    public static Collection<CentralRepoAccount> getAccountsWithIdentifier(String accountIdentifier) throws CentralRepoException {
       
        String queryClause = ACCOUNTS_QUERY_CLAUSE
                + " WHERE LOWER(accounts.account_unique_identifier) = LOWER('" + accountIdentifier + "')";

        AccountsQueryCallback queryCallback = new AccountsQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);

        return queryCallback.getAccountsList();
    }
    
    /**
     * Get all central repo accounts.
     *
     * @return Collection of all accounts with identifier matching the given identifier, may
     * be empty.
     * 
     * @throws CentralRepoException If there is an error in getting the accounts.
     */
    public static Collection<CentralRepoAccount> getAllAccounts() throws CentralRepoException {
       
        String queryClause = ACCOUNTS_QUERY_CLAUSE;

        AccountsQueryCallback queryCallback = new AccountsQueryCallback();
        CentralRepository.getInstance().executeSelectSQL(queryClause, queryCallback);

        return queryCallback.getAccountsList();
    }
    
}

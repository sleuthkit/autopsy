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

import org.sleuthkit.datamodel.Account;


/**
 * This class abstracts an Account as stored in the CR database.
 */
public final class CentralRepoAccount {
    
	// primary key in the Accounts table in CR database
	private final long accountId;

	private final CentralRepoAccountType accountType;
        // type specifc unique account id
	private final String typeSpecificId;
        
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
    
    public CentralRepoAccount(long accountId, CentralRepoAccountType accountType, String typeSpecificId) {
		this.accountId = accountId;
		this.accountType = accountType;
		this.typeSpecificId = typeSpecificId;
	}

	/**
	 * Gets unique identifier (assigned by a provider) for the account. Example
	 * includes an email address, a phone number, or a website username.
	 *
	 * @return type specific account id.
	 */
	public String getTypeSpecificId() {
		return this.typeSpecificId;
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
	public long getAccountId() {
		return this.accountId;
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 43 * hash + (int) (this.accountId ^ (this.accountId >>> 32));
		hash = 43 * hash + (this.accountType != null ? this.accountType.hashCode() : 0);
		hash = 43 * hash + (this.typeSpecificId != null ? this.typeSpecificId.hashCode() : 0);
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
		if (this.accountId != other.getAccountId()) {
			return false;
		}
		if ((this.typeSpecificId == null) ? (other.getTypeSpecificId() != null) : !this.typeSpecificId.equals(other.getTypeSpecificId())) {
			return false;
		}
		if (this.accountType != other.getAccountType() && (this.accountType == null || !this.accountType.equals(other.getAccountType()))) {
			return false;
		}
		return true;
	}
    
}

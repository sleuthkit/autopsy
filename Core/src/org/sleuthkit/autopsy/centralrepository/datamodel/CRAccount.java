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
public final class CRAccount {
    
	// primary key in the Accounts table in CR database
	private final long account_id;

	private final CRAccountType crAccountType;
	private final String typeSpecificID;
        
    /**
     * A CRAccounType encapsulates an account type and the correlation type
     * that it maps to.
     */
    public static final class CRAccountType {

        // id is the primary key in the account_types table
        private final int crAccountTypeID;
        private final Account.Type acctType;
        private final int correlationTypeID;

        CRAccountType(int acctTypeID, Account.Type acctType, int correlation_type_id) {
            this.acctType = acctType;
            this.correlationTypeID = correlation_type_id;
            this.crAccountTypeID = acctTypeID;
        }
        

        /**
         * @return the acctType
         */
        public Account.Type getAcctType() {
            return acctType;
        }

        public int getCorrelationTypeId() {
            return this.correlationTypeID;
        }

        public int getCRAccountTypeId() {
            return this.crAccountTypeID;
        }
    }
    
    public CRAccount(long account_id, CRAccountType accountType, String typeSpecificId) {
		this.account_id = account_id;
		this.crAccountType = accountType;
		this.typeSpecificID = typeSpecificId;
	}

	/**
	 * Gets unique identifier (assigned by a provider) for the account. Example
	 * includes an email address, a phone number, or a website username.
	 *
	 * @return type specific account id.
	 */
	public String getTypeSpecificID() {
		return this.typeSpecificID;
	}

	/**
	 * Gets the account type
	 *
	 * @return account type
	 */
	public CRAccountType getAccountType() {
		return this.crAccountType;
	}

	/**
	 * Gets the unique row id for this account in the database.
	 *
	 * @return unique row id.
	 */
	public long getAccountID() {
		return this.account_id;
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 43 * hash + (int) (this.account_id ^ (this.account_id >>> 32));
		hash = 43 * hash + (this.crAccountType != null ? this.crAccountType.hashCode() : 0);
		hash = 43 * hash + (this.typeSpecificID != null ? this.typeSpecificID.hashCode() : 0);
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
		final CRAccount other = (CRAccount) obj;
		if (this.account_id != other.getAccountID()) {
			return false;
		}
		if ((this.typeSpecificID == null) ? (other.getTypeSpecificID() != null) : !this.typeSpecificID.equals(other.getTypeSpecificID())) {
			return false;
		}
		if (this.crAccountType != other.getAccountType() && (this.crAccountType == null || !this.crAccountType.equals(other.getAccountType()))) {
			return false;
		}
		return true;
	}
    
}

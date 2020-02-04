/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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

/**
 * An organization in the Central Repository database
 */
public class CentralRepoOrganization {

    private int orgID;
    private String name;
    private String pocName;
    private String pocEmail;
    private String pocPhone;

    CentralRepoOrganization(
            int orgID,
            String name,
            String pocName,
            String pocEmail,
            String pocPhone) {
        this.orgID = orgID;
        this.name = name;
        this.pocName = pocName;
        this.pocEmail = pocEmail;
        this.pocPhone = pocPhone;
    }

    public CentralRepoOrganization(
            String name,
            String pocName,
            String pocEmail,
            String pocPhone) {
        this(-1, name, pocName, pocEmail, pocPhone);
    }

    public CentralRepoOrganization(
            String name) {
        this(-1, name, "", "", "");
    }

    public static CentralRepoOrganization getDefault() {
        // TODO: when we allow the user to configure/specify the default organization
        //  this will return it.
        return null;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("(");
        str.append("orgID=").append(Integer.toString(getOrgID()));
        str.append("name=").append(getName());
        str.append("pocName=").append(getPocName());
        str.append("pocEmail=").append(getPocEmail());
        str.append("pocPhone=").append(getPocPhone());
        str.append(")");
        return str.toString();
    }

    /**
     * @return the orgID
     */
    public int getOrgID() {
        return orgID;
    }

    /**
     * @param orgID the orgID to set
     */
    void setOrgID(int orgID) {
        this.orgID = orgID;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the pocName
     */
    public String getPocName() {
        return pocName;
    }

    /**
     * @param pocName the pocName to set
     */
    public void setPocName(String pocName) {
        this.pocName = pocName;
    }

    /**
     * @return the pocEmail
     */
    public String getPocEmail() {
        return pocEmail;
    }

    /**
     * @param pocEmail the pocEmail to set
     */
    public void setPocEmail(String pocEmail) {
        this.pocEmail = pocEmail;
    }

    /**
     * @return the pocPhone
     */
    public String getPocPhone() {
        return pocPhone;
    }

    /**
     * @param pocPhone the pocPhone to set
     */
    public void setPocPhone(String pocPhone) {
        this.pocPhone = pocPhone;
    }
}

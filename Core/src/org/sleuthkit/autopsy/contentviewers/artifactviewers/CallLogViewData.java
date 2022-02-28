/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.contentviewers.artifactviewers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the information to be displayed about a call log artifact.
 */
final class CallLogViewData {

    private String fromAccount = null;
    private String toAccount = null;
    
    private String hostName = null;

    // account identifier of the device owner, if known.
    // will be one of the to or from account.
    private String localAccountId = null;

    private String direction;
    private String dateTimeStr = null;
    private String duration = null;

    // Account identifers of other parties in the call.
    private Collection<String> otherParties = new ArrayList<>();

    private Map<String, String> otherAttributes = new HashMap<>();

    private String dataSourceName = null;

    private List<String> toContactNameList = null;
    private List<String> fromContactNameList = null;

    /**
     * Constructor.
     *
     * @param fromAccount From account identifier, may be null;
     * @param toAccount   To account identifier, may be null;
     */
    CallLogViewData(String fromAccount, String toAccount) {
        this(fromAccount, toAccount, null);
    }

    /**
     * Constructor.
     *
     * @param fromAccount From account identifier, may be null;
     * @param toAccount   To account identifier, may be null;
     * @param direction   Direction, may be null.
     */
    CallLogViewData(String fromAccount, String toAccount, String direction) {
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.direction = direction;
    }

    String getFromAccount() {
        return fromAccount;
    }

    void setFromAccount(String fromAccount) {
        this.fromAccount = fromAccount;
    }

    String getToAccount() {
        return toAccount;
    }

    void setToAccount(String toAccount) {
        this.toAccount = toAccount;
    }

    String getDirection() {
        return direction;
    }

    void setDirection(String direction) {
        this.direction = direction;
    }

    String getDataSourceName() {
        return dataSourceName;
    }

    void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    String getDateTimeStr() {
        return dateTimeStr;
    }

    void setDateTimeStr(String dateTimeStr) {
        this.dateTimeStr = dateTimeStr;
    }

    String getDuration() {
        return duration;
    }

    void setDuration(String duration) {
        this.duration = duration;
    }

    Collection<String> getOtherParties() {
        return Collections.unmodifiableCollection(otherParties);
    }

    void setOtherParties(Collection<String> otherParticipants) {
        if (otherParticipants != null) {
            this.otherParties = new ArrayList<>(otherParticipants);
        }
    }

    public Map<String, String> getOtherAttributes() {
        return Collections.unmodifiableMap(otherAttributes);
    }

    public void setOtherAttributes(Map<String, String> otherAttributes) {
        if (otherAttributes != null) {
            this.otherAttributes = new HashMap<>(otherAttributes);
        }
    }

    public String getLocalAccountId() {
        return localAccountId;
    }

    public void setLocalAccountId(String localAccountId) {
        this.localAccountId = localAccountId;
    }

    public void setToContactNameList(List<String> contactNameList) {
        if (contactNameList != null) {
            this.toContactNameList = new ArrayList<>(contactNameList);
        } else {
            this.toContactNameList = new ArrayList<>();
        }
    }

    public List<String> getToContactNameList() {
        return Collections.unmodifiableList(this.toContactNameList);
    }

    public void setFromContactNameList(List<String> contactNameList) {
        if (contactNameList != null) {
            this.fromContactNameList = new ArrayList<>(contactNameList);
        } else {
            this.fromContactNameList = new ArrayList<>();
        }
    }

    public List<String> getFromContactNameList() {
        return Collections.unmodifiableList(this.fromContactNameList);
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }
}

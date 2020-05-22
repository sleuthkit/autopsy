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
package org.sleuthkit.autopsy.contentviewers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the information to be displayed about a call log artifact.
 */
final class CallLogViewData {

    // primary to/from number/adddress/accountId
    private String number;
    private String numberTypeDesignator;  // to for from designator
    private String name = null;
    private String direction;
    private String dateTimeStr = null;
    private String duration = null;
    private Collection<String> otherRecipients = new ArrayList<>();
    private String dataSourceName = null;
    private String dataSourceDeviceId = null;
    private String localAccountId = null; // number/accountId of device owner, may not be always known
    private Map<String, String> otherAttributes = new HashMap<>();

    CallLogViewData(String number) {
        this(number, null);
    }

    CallLogViewData(String number, String direction) {
        this.number = number;
        this.direction = direction;
    }

    String getNumber() {
        return number;
    }

    void setNumber(String number) {
        this.number = number;
    }

    public String getNumberDesignator() {
        return numberTypeDesignator;
    }

    public void setNumberDesignator(String numberDesignator) {
        this.numberTypeDesignator = numberDesignator;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
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

    String getDataSourceDeviceId() {
        return dataSourceDeviceId;
    }

    void setDataSourceDeviceId(String dataSourceDeviceId) {
        this.dataSourceDeviceId = dataSourceDeviceId;
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

    Collection<String> getOtherRecipients() {
        return Collections.unmodifiableCollection(otherRecipients);
    }

    void setOtherRecipients(Collection<String> otherParticipants) {
        if (otherParticipants != null) {
            this.otherRecipients = new ArrayList<>(otherParticipants);
        }
    }

    public Map<String, String> getOtherAttributes() {
        return Collections.unmodifiableMap(otherAttributes);
    }

    public void setOtherAttributes(Map<String, String> otherAttributes) {
        if (otherRecipients != null) {
            this.otherAttributes = new HashMap<>(otherAttributes);
        }
    }

    public String getLocalAccountId() {
        return localAccountId;
    }

    public void setLocalAccountId(String localAccountId) {
        this.localAccountId = localAccountId;
    }

}

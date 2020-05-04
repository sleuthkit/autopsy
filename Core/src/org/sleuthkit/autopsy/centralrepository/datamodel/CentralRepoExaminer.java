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

/**
 * Encapsulates the concept of an examiner.
 */
final public class CentralRepoExaminer {

    private final long id;  // Row id in the examiners table in central repo database.
    private final String loginName;

    public CentralRepoExaminer(long id, String loginName) {
        this.id = id;
        this.loginName = loginName;
    }

    /**
     * Returns the id.
     *
     * @return id
     */
    public long getId() {
        return id;
    }

    /**
     * Returns the login name of examiner.
     *
     * @return login name
     */
    public String getLoginName() {
        return this.loginName;
    }

}

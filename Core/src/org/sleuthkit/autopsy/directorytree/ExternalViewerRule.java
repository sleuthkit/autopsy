/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.util.Objects;

/**
 * An object that represents an association between a MIME type or extension
 * name to a user defined executable.
 */
class ExternalViewerRule implements Comparable<ExternalViewerRule> {

    private final String name;
    private final String exePath;
    private final RuleType ruleType;

    enum RuleType {
        MIME, EXT
    }

    /**
     * Creates a new ExternalViewerRule
     *
     * @param name    MIME type or extension
     * @param exePath Absolute path of the exe file
     * @param type    RuleType of the rule, either MIME or EXT
     */
    ExternalViewerRule(String name, String exePath, RuleType type) {
        this.name = name;
        this.exePath = exePath;
        this.ruleType = type;
    }

    String getName() {
        return name;
    }

    String getExePath() {
        return exePath;
    }

    RuleType getRuleType() {
        return ruleType;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Only one association is allowed per MIME type or extension, so rules are
     * equal if the names are the same.
     */
    @Override
    public boolean equals(Object other) {
        if (other != null && other instanceof ExternalViewerRule) {
            ExternalViewerRule that = (ExternalViewerRule) other;
            if (this.getName().equals(that.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public int compareTo(ExternalViewerRule other) {
        return this.getName().compareTo(other.getName());
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.directorytree;

import java.util.Objects;

/**
 * An object that represents an association between a MIME type or extension
 * name to a user defined executable.
 */
class ExternalViewerRule {

    private final String name;
    private final String exePath;

    /**
     * Creates a new ExternalViewerRule
     *
     * @param name MIME type or extension
     * @param exePath Absolute path of the exe file
     */
    ExternalViewerRule(String name, String exePath) {
        this.name = name;
        this.exePath = exePath;
    }

    String getName() {
        return name;
    }

    String getExePath() {
        return exePath;
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
}

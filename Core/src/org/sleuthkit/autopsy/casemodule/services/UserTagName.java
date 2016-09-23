/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/
package org.sleuthkit.autopsy.casemodule.services;

import java.util.Objects;

/**
 * Because the DTO TagName constructor should not be called outside of its
 * class package, CustomTagName is used in this tags managers panel for the
 * purpose of tracking the description and color of each tag name.
 */
class UserTagName implements Comparable<UserTagName> {
    
    private final String displayName;
    private final String description;
    private final String colorName;
    
    UserTagName(String displayName, String description, String colorName) {
        this.displayName = displayName;
        this.description = description;
        this.colorName = colorName;
    }
    
    String getDisplayName() {
        return displayName;
    }
    
    String getDescription() {
        return description;
    }
    
    String getColorName() {
        return colorName;
    }
    
    @Override
    public int compareTo(UserTagName other) {
        return this.getDisplayName().toLowerCase().compareTo(other.getDisplayName().toLowerCase());
    }
    
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.displayName);
        return hash;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UserTagName)) {
            return false;
        }
        UserTagName thatTagName = (UserTagName) obj;
        return this.getDisplayName().equals(thatTagName.getDisplayName());
    }
    
    @Override
    public String toString() {
        return displayName;
    }
    
    /**
     * @return A string representation of the tag name in the format that is
     * used by the properties file.
     */
    public String toSettingsFormat() {
        return displayName + "," + description + "," + colorName;
    }
}

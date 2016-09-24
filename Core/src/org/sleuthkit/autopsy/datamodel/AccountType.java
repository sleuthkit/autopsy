package org.sleuthkit.autopsy.datamodel;

public enum AccountType {
    CREDIT_CARD("Credit Card"), OTHER("Other");

    public String getDisplayName() {
        return displayName;
    }

    private final String displayName;

    private AccountType(String displayName) {
        this.displayName = displayName;
    }
}

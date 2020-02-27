/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 *
 * @author gregd
 */
public class PostgresConnectionSettings {
    private final static String DB_NAMES_REGEX = "[a-z][a-z0-9_]*"; // only lower case
    private final static String DB_USER_NAMES_REGEX = "[a-zA-Z]\\w*";
    
    private String host;
    private int port;
    private String dbName;
    private int bulkThreshold;
    private String userName;
    private String password;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDbName() {
        return dbName;
    }

    public int getBulkThreshold() {
        return bulkThreshold;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }
    
    
    private static void validateStr(String s, String errMessage) throws CentralRepoException {
        if (null == s || s.isEmpty())
            throw new CentralRepoException(errMessage);
    }
    
    private static void validateRegex(String s, String pattern, String errMessage) throws CentralRepoException {
        if (!Pattern.matches(pattern, s))
            throw new CentralRepoException(errMessage);
    }
    
    private static void validateNum(int num, Integer min, Integer max, String errMessage) throws CentralRepoException {
        if ((min != null && num < min) || (max != null && num > max))
            throw new CentralRepoException(errMessage);
    }
    
    
    /**
     * @param host the host to set
     */
    public void setHost(String host) throws CentralRepoException {
        validateStr(host, "Invalid host name. Cannot be empty.");
        this.host = host;
    }


    /**
     * @param port the port to set
     */
    public void setPort(int port) throws CentralRepoException {
        validateNum(port, 1, 65535, "Invalid port. Must be a number greater than 0.");
        this.port = port;
    }


    /**
     * @param dbName the dbName to set
     */
    public void setDbName(String dbName) throws CentralRepoException {
        validateStr(dbName, "Invalid database name. Cannot be empty."); // NON-NLS
        validateRegex(dbName, DB_NAMES_REGEX, 
            "Invalid database name. Name must start with a lowercase letter and can only contain lowercase letters, numbers, and '_'."); // NON-NLS

        this.dbName = dbName.toLowerCase();
    }

    
    /**
     * @param bulkThreshold the bulkThreshold to set
     */
    public void setBulkThreshold(int bulkThreshold) throws CentralRepoException {
        validateNum(bulkThreshold, 1, null, "Invalid bulk threshold.");
        this.bulkThreshold = bulkThreshold;
    }

    
    /**
     * @param userName the userName to set
     */
    public void setUserName(String userName) throws CentralRepoException {
        validateStr(userName, "Invalid user name. Cannot be empty."); // NON-NLS
        validateRegex(userName, DB_USER_NAMES_REGEX, 
            "Invalid user name. Name must start with a letter and can only contain letters, numbers, and '_'.");
        
        this.userName = userName;
    }

    
    /**
     * @param password the password to set
     */
    public void setPassword(String password) throws CentralRepoException {
        validateStr(password, "Invalid user password. Cannot be empty.");
        this.password = password;
    }
    
    
    
    @Override
    public int hashCode() {
        int hash = 3;
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
        final PostgresConnectionSettings other = (PostgresConnectionSettings) obj;
        if (this.port != other.port) {
            return false;
        }
        if (this.bulkThreshold != other.bulkThreshold) {
            return false;
        }
        if (!Objects.equals(this.host, other.host)) {
            return false;
        }
        if (!Objects.equals(this.dbName, other.dbName)) {
            return false;
        }
        if (!Objects.equals(this.userName, other.userName)) {
            return false;
        }
        if (!Objects.equals(this.password, other.password)) {
            return false;
        }
        return true;
    }
}

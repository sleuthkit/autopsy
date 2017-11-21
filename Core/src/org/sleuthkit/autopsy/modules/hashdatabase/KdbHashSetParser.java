/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class KdbHashSetParser implements HashSetParser {
    private final String JDBC_DRIVER = "org.sqlite.JDBC"; // NON-NLS
    private final String JDBC_BASE_URI = "jdbc:sqlite:"; // NON-NLS

    private final String filename;        // Name of the input file (saved for logging)
    private final long totalHashes;       // Estimated number of hashes  
    private int totalHashesRead = 0;       // Number of hashes that have been read
    private Connection conn;
    private Statement statement;
    private ResultSet resultSet;
    
    
    KdbHashSetParser(String filename) throws TskCoreException{
        this.filename = filename;
        
        conn = null;
        statement = null;
        resultSet = null;
        
        try{
            // Open the database
            StringBuilder connectionURL = new StringBuilder();
            connectionURL.append(JDBC_BASE_URI);
            connectionURL.append(filename);
            Class.forName(JDBC_DRIVER);
            conn = DriverManager.getConnection(connectionURL.toString());                 
                
            // Get the number of hashes in the table
            statement = conn.createStatement();
            resultSet = statement.executeQuery("SELECT count(*) AS count FROM hashes");
            if (resultSet.next()) {
                totalHashes = resultSet.getLong("count");
            } else {
                close();
                throw new TskCoreException("Error getting hash count from database " + filename);
            }
            
            // Get the hashes
            resultSet = statement.executeQuery("SELECT md5 FROM hashes");
            
            // At this point, getNextHash can read each hash from the result set
            
        } catch (ClassNotFoundException | SQLException ex){
            throw new TskCoreException("Error opening/reading database " + filename, ex);
        }
    
    }
    
    /**
     * Get the next hash to import
     * @return The hash as a string, or null if the end of file was reached without error
     * @throws TskCoreException 
     */
    @Override
    public String getNextHash() throws TskCoreException {
  
        try{
            if(resultSet.next()){
                byte[] hashBytes = resultSet.getBytes("md5");
                StringBuilder sb = new StringBuilder();
                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }

                if(sb.toString().length() != 32){
                    throw new TskCoreException("Hash has incorrect length: " + sb.toString());
                }   
                
                totalHashesRead++;
                return sb.toString();
            } else {
                throw new TskCoreException("Could not read expected number of hashes from database " + filename);
            }
        } catch (SQLException ex){
            throw new TskCoreException("Error reading hash from result set for database " + filename, ex);
        }
    }
    
    /**
     * Check if there are more hashes to read
     * @return true if we've read all expected hash values, false otherwise
     */
    @Override
    public boolean doneReading() {
        return(totalHashesRead >= totalHashes);
    }
    
    /**
     * Get the expected number of hashes in the file.
     * This number can be an estimate.
     * @return The expected hash count
     */
    @Override
    public long getExpectedHashCount() { 
        return totalHashes;
    }
    
    /**
     * Closes the import file
     */
    @Override
    public final void close() {
        if(statement != null){
            try {
                statement.close();
            } catch (SQLException ex) {
                Logger.getLogger(KdbHashSetParser.class.getName()).log(Level.SEVERE, "Error closing prepared statement.", ex);
            }
        }
        
        if(resultSet != null){
            try {
                resultSet.close();
            } catch (SQLException ex) {
                Logger.getLogger(KdbHashSetParser.class.getName()).log(Level.SEVERE, "Error closing result set.", ex);
            }
        }
        
        if(conn != null){
            try {
                conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(KdbHashSetParser.class.getName()).log(Level.SEVERE, "Error closing connection.", ex);
            }
        }        
    }
}

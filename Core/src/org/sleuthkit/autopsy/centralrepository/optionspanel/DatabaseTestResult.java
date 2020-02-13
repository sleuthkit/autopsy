/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.optionspanel;

enum DatabaseTestResult {
    UNTESTED,
    CONNECTION_FAILED,
    SCHEMA_INVALID,
    DB_DOES_NOT_EXIST,
    TESTEDOK;
}
    

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.testing;

import org.netbeans.jemmy.Timeout; //unused import
/**
 *
 * @author 
 */
public class PMDTest {
    private long test;
    private String pmd;
    
    public PMDTest(long test) {
        pmd = "Test PMD settings";
        this.test = setTest(test);
    }
    
    public long setTest(long test){
        String unused;
        return test++;
    }
    
    public String getPmd() {
        return pmd;
    }
    
    public String getCheckVarLocation() {
        return check_var_location;
    }
    private void testPMD() {
        pmd = "PMD Test unused method.";
    }
    
    private String check_var_location = "Not a good position.";
}
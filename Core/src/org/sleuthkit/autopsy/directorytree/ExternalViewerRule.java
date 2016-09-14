/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.directorytree;

/**
 *
 * @author smori
 */
class ExternalViewerRule {
    private String name;
    private String exePath;
    
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
    
    void editName(String newName) {
        name = newName;
    }
    
    void editExePath(String newExePath) {
        exePath = newExePath;
    }
    
}

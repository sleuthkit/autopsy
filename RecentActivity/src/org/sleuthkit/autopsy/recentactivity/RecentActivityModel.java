/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;
import java.util.ArrayList;
import javax.swing.AbstractListModel;
import javax.swing.ListModel;


public class RecentActivityModel {
    
  private String name;
  private ArrayList<BrowserModel> data;
        
    public RecentActivityModel (){
    
    }
    
    public RecentActivityModel(String name, ArrayList<BrowserModel> data) {
        this.name = name;
        this.data = data;
    }
    
}


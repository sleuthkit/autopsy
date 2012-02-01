/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.util.ArrayList;
import javax.swing.AbstractListModel;
import javax.swing.ListModel;

/**
 *
 * @author Alex
 */
public abstract class BrowserModel extends AbstractListModel implements ListModel {
    
  private  int id;
  private  int visit_count;
  private  String last_accessed;
  private  String from_visit;
  private  String url;
  private  String title;
  private  String type;  
  private String browser;
  
  public BrowserModel(){
       
    }
    
   public BrowserModel(int ID, String browser, int count, String accessed, String reference, String path, String name) {
        this.id = ID;
        this.type = type;
        this.browser = browser;
        this.visit_count = count;
        this.last_accessed = accessed;
        this.from_visit = reference;
        this.url = path;
        this.title = name;
        
    }
    
   public ArrayList<BrowserModel> getresults()
   {
       return null;
   }
    
}

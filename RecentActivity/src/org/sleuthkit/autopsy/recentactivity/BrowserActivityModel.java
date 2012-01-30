/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.util.ArrayList;
import java.util.Map;

/**
 *
 * @author Alex
 */
public class BrowserActivityModel {
 
 int count = 0;
 private ArrayList<Map<BrowserActivity,Map>> BAM = new ArrayList<Map<BrowserActivity,Map>>();
  public BrowserActivityModel(){
      init();
  }
  
  public void init()
  {
      
  }

}


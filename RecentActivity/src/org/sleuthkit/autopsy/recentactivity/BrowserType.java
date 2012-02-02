/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author arivera
 */
public enum BrowserType {
   IE(0), //Internet Explorer
   FF(1), //Firefox
   CH(2); //Chrome
    private static final Map<Integer,BrowserType> lookup
            = new HashMap<Integer,BrowserType>();

    static {
        for(BrowserType bt : values())
            lookup.put(bt.type, bt);
    }


   private int type;

   private BrowserType(int type)
   {
      this.type = type;
   }

    public int getType() { return type; }

    public static BrowserType get(int type) {
        switch(type) {
            case 0: return IE;
            case 1: return FF;
            case 2: return CH;
        }
        return null;
    }

}

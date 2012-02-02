/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author arivera
 */
public enum BrowserActivityType {
   Cookies(0),
   Url(1),
   Bookmarks(2);
    private static final Map<Integer,BrowserActivityType> lookup
            = new HashMap<Integer,BrowserActivityType>();

    static {
        for(BrowserActivityType bat : values())
            lookup.put(bat.type, bat);
    }


   private int type;

   private BrowserActivityType(int type)
   {
      this.type = type;
   }

    public int getType() { return type; }

    public static BrowserActivityType get(int type) {
        switch(type) {
            case 0: return Cookies;
            case 1: return Url;
            case 2: return Bookmarks;
        }
        return null;
    }

}

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
public enum BrowserActivity {
   IE(0),
   FF(1),
   CH(2);
    private static final Map<Integer,BrowserActivity> lookup
            = new HashMap<Integer,BrowserActivity>();

    static {
        for(BrowserActivity bat : values())
            lookup.put(bat.type, bat);
    }


   private int type;

   private BrowserActivity(int type)
   {
      this.type = type;
   }

    public int getType() { return type; }

    public static BrowserActivity get(int type) {
        switch(type) {
            case 0: return IE;
            case 1: return FF;
            case 2: return CH;
        }
        return null;
    }

}
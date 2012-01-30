/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.Array;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataExplorer;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.KeyValueThing;
import java.util.Random.*;
import java.util.*;
import java.util.logging.Logger;


@ServiceProvider(service = DataExplorer.class)
public class RecentActivityDataExplorer implements DataExplorer {
    
    RecentActivityTopComponent tc;
    
    static final int NUMBER_THING_ID = 41234;
   private final Logger logger = Logger.getLogger(this.getClass().getName());
    private Collection<KeyValueThing> things = new ArrayList<KeyValueThing>();
    
    //Empty Constructor
    public RecentActivityDataExplorer() {
        tc = new RecentActivityTopComponent(this);
        tc.setName("Recent Activity");
        tc.edx.tc.setbrowsercheckboxes(0,0,0,0,0);
    }

    @Override
    public org.openide.windows.TopComponent getTopComponent() {
        return tc;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // nothing to do in simple example
    }
    
  
    
    public void getallactivity()
    {
      Firefox ffre = new Firefox();
      ffre.getffdb();
      Chrome chre = new Chrome();
      chre.getchdb();
    }
    
void makeNodes() 
    {
        things.clear();
        ExtractRegistry eree = new ExtractRegistry();
        eree.getregistryfiles();
        Firefox ffre = new Firefox();
        ffre.getffdb();    
        Chrome chre = new Chrome();
        chre.getchdb();
        ExtractIE eere = new ExtractIE();
        eere.parsePascoResults();
        
        
        
        ArrayList<HashMap<String,Object>> IEresults = eere.PASCO_RESULTS_LIST;
        int cookiescount = 0;
        int bookmarkscount = 0;
        int iecount = IEresults.size();
        bookmarkscount = ffre.bookmarks.size() + chre.bookmarks.size();
        cookiescount = ffre.cookies.size() + chre.cookies.size();
        int ffcount = ffre.als.size();
        int chcount = chre.als.size();
        ExtractRegistry regob = new ExtractRegistry();
        regob.getregistryfiles();
        
          
        for(Map<String,Object> FFmap : ffre.als){
           things.add(new KeyValueThing("FireFox", FFmap, NUMBER_THING_ID));  
        }
        
         for(HashMap<String,Object> IEmap : IEresults){
           things.add(new KeyValueThing("Internet Explorer", IEmap, NUMBER_THING_ID));  
        }
        for(Map<String,Object> CHmap : chre.als){
           things.add(new KeyValueThing("Chrome", CHmap, NUMBER_THING_ID));  
        }
        
        for(Map<String,Object> FFCookies : ffre.cookies){
             things.add(new KeyValueThing("Cookie", FFCookies, NUMBER_THING_ID)); 
        }
        for(Map<String,Object> FFBookmark : ffre.bookmarks){
             things.add(new KeyValueThing("Bookmark", FFBookmark, NUMBER_THING_ID)); 
        }
        
        for(Map<String,Object> CHCookies : chre.cookies){
             things.add(new KeyValueThing("Cookie", CHCookies, NUMBER_THING_ID)); 
        }
        
        for(Map<String,Object> CHBookmark : chre.bookmarks){
             things.add(new KeyValueThing("Bookmark", CHBookmark, NUMBER_THING_ID)); 
        }
      
        Children childThingNodes = Children.create(new RecentActivityKeyValueChildFactory(things), true);
        tc.setbrowsercheckboxes(iecount,ffcount,chcount,cookiescount,bookmarkscount);
        Node rootNode = new AbstractNode(childThingNodes);
        String pathText = "Recent User Activity";
        TopComponent searchResultWin = DataResultTopComponent.createInstance("Recent Activity", pathText, rootNode, things.size()); 
         
        searchResultWin.requestActive(); // make it the active top component
       
    }
}

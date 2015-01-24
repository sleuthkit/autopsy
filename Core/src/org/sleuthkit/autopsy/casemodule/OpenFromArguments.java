/*
// * To change this template, choose Tools | Templates
// * and open the template in the editor.
// */
package org.sleuthkit.autopsy.casemodule;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.netbeans.spi.sendopts.*;
import org.openide.util.lookup.ServiceProvider;


@ServiceProvider(service = OptionProcessor.class)
public class OpenFromArguments extends OptionProcessor
{

    private String path ="";
    private Option option1=Option.defaultArguments();
 
  
    @Override
    protected Set getOptions()
    {
        Set set = new HashSet();
        set.add(option1);
        return set;
    }
    @Override
       public void process(Env env, Map<Option,String[]> values) {
     if (values.containsKey(option1))
     {
         path=values.get(option1)[0];
     }
   }
    public String getArg(){
        return path;
    }
}



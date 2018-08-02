/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.core;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.spi.sendopts.Env;
import org.netbeans.spi.sendopts.Option;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 * This class can be used to add command line options to Autopsy
 * 
 */
@ServiceProvider(service=OptionProcessor.class)
public class AutopsyOptionProcessor extends OptionProcessor {
    
    private static final Logger logger = Logger.getLogger(AutopsyOptionProcessor.class.getName());
    private final Option liveAutopsyOption = Option.withoutArgument('l', "liveAutopsy");


    @Override
    protected Set<Option> getOptions() {
        Set<Option> set = new HashSet<>();
        set.add(liveAutopsyOption);
        return set;
    }

    @Override
    protected void process(Env env, Map<Option, String[]> values) throws CommandException {
       if(values.containsKey(liveAutopsyOption)){
           try {
               RuntimeProperties.setAutopsyLive(true);
           } catch (RuntimeProperties.RuntimePropertiesException ex) {
               logger.log(Level.SEVERE, ex.getMessage(), ex);
           }
       }
    }
    
}

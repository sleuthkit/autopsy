/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2017 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * This class can be used to add command line options to Autopsy
 * To add more options to autopsy, create a Option variable and add it to the set in getOptions method
 * Do your logic for that option in the process method
 */
@ServiceProvider(service=OptionProcessor.class)
public class AutopsyOptionProcessor extends OptionProcessor {
    
    private static final Logger logger = Logger.getLogger(AutopsyOptionProcessor.class.getName());
    private final Option liveAutopsyOption = Option.optionalArgument('l', "liveAutopsy");
    private final static String PROP_BASECASE = "LBL_BaseCase_PATH";


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
               RuntimeProperties.setRunningInTarget(true);
               String[] dir= values.get(liveAutopsyOption);
               String directory = dir == null ? PlatformUtil.getUserDirectory().toString() : dir[0];
               ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE, directory);
           } catch (RuntimeProperties.RuntimePropertiesException ex) {
               logger.log(Level.SEVERE, ex.getMessage(), ex);
           }
       }
    }
    
}

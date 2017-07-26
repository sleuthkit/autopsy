/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2017 Basis Technology Corp.
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

import org.netbeans.api.sendopts.CommandException;
import org.netbeans.spi.sendopts.Arg;
import org.netbeans.spi.sendopts.ArgsProcessor;
import org.netbeans.spi.sendopts.Env;
import org.openide.util.lookup.ServiceProvider;

/**
 * This class will retrieve command-line arguments.
 */
@ServiceProvider(service = ArgsProcessor.class)
public class ArgumentsProcessor implements ArgsProcessor {
    private static boolean autoIngestService = false;
    private static String sharedConfigPath = null;
    
    @Arg(longName="autoingestservice")
    public boolean argAutoIngestService;
    
    @Arg(longName="sharedconfig")
    public String argSharedConfig;
    
    public ArgumentsProcessor() {
    }

    @Override
    public void process(Env env) throws CommandException {
        if(argAutoIngestService)
            autoIngestService = true;
        
        if(argSharedConfig != null) {
            sharedConfigPath = argSharedConfig;
        }
    }
    
    /**
     * @return true if "--autoingestservice" argument supplied, otherwise false.
     */
    public static boolean isAutoIngestService() {
        return autoIngestService;
    }
    
    /**
     * @return true if "--sharedconfig" argument supplied, otherwise false.
     */
    public static String getSharedConfigPath() {
        return sharedConfigPath;
    }
}
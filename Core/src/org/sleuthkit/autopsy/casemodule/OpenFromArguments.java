/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.spi.sendopts.Env;
import org.netbeans.spi.sendopts.Option;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 * Allows Autopsy to get path to .aut file passed in via associating the file
 * type in Windows.
 */
@ServiceProvider(service = OptionProcessor.class)
public class OpenFromArguments extends OptionProcessor {

    /*
     * Stores the .aut file if it was passed in as argument
     */
    private String autPath = "";
    private final Option option1 = Option.defaultArguments();

    @Override
    protected Set<Option> getOptions() {
        final Set<Option> options = new HashSet<>();
        options.add(option1);
        return options;
    }

    @Override
    protected void process(Env env, Map<Option, String[]> maps) throws CommandException {
        if (maps.containsKey(option1)) {
            autPath = maps.get(option1)[0];
        }
    }

    public String getDefaultArg() {
        return autPath;
    }
}

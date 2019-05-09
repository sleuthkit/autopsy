/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commandlineingest;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that contains list of input parameters passed in via command line.
 */
class CommandLineCommand {
 
    /**
     * An enumeration of command types.
     */
    static enum CommandType {        
        CREATE_CASE,
        ADD_DATA_SOURCE,
        RUN_INGEST;
    }
    
    /**
     * An enumeration of input types.
     */
    static enum InputType {        
        CASE_NAME,
        CASES_BASE_DIR_PATH,
        CASE_FOLDER_DIR_PATH,
        DATA_SOURCE_DIR_PATH;
    }
    
    private final CommandType type;
    private final Map<String, String> inputs = new HashMap<>();
    
    CommandLineCommand(CommandType type) {
        this.type = type;
    }
    
    void addInputValue(String inputName, String inputValue) {
        inputs.put(inputName, inputValue);
    }
    
    /**
     * @return the inputs
     */
    public Map<String, String> getInputs() {
        return inputs;
    }

    /**
     * @return the type
     */
    public CommandType getType() {
        return type;
    }    
}

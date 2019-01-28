/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.openide.util.NbBundle;

/**
 * Manager for present state of errors on the Common Files Search.
 */
class UserInputErrorManager {
    
    static final int FREQUENCY_PERCENTAGE_OUT_OF_RANGE_KEY = 1; 
    static final int NO_FILE_CATEGORIES_SELECTED_KEY = 2;
    
    private final Map<Integer, ErrorMessage> currentErrors;
    
    /**
     * Construct a new ErrorManager which can be used to track the status
     * of all known error states, retrieve error messages, and determine if 
     * anything is in an error state.
     */
    @NbBundle.Messages({
        "UserInputErrorManager.frequency=Invalid Frequency Percentage: 0 < % < 100.",
        "UserInputErrorManager.categories=No file categories are included in the search."})
    UserInputErrorManager (){
        
        //when new errors are needed for the dialog, define a key and a value
        //  and add them to the map.
        
        this.currentErrors = new HashMap<>();
        this.currentErrors.put(FREQUENCY_PERCENTAGE_OUT_OF_RANGE_KEY, new ErrorMessage(Bundle.UserInputErrorManager_frequency()));
        this.currentErrors.put(NO_FILE_CATEGORIES_SELECTED_KEY, new ErrorMessage(Bundle.UserInputErrorManager_categories()));
    }
    
    /**
     * Toggle the given error message on, or off
     * @param errorId the error to toggle
     * @param errorState true for on, false for off
     */
    void setError(int errorId, boolean errorState){
        if(this.currentErrors.containsKey(errorId)){
            this.currentErrors.get(errorId).setStatus(errorState);
        } else {
            throw new IllegalArgumentException(String.format("The given errorId is not mapped to an ErrorMessage: %s.", errorId));
        }
    }
    
    /**
     * Are any user settings presently in an error state?
     * @return true for yes, else false
     */
    boolean anyErrors(){
        return this.currentErrors.values().stream().anyMatch(errorMessage -> errorMessage.isErrorSet() == true);
    }
    
    /**
     * Get a list of distinct string messages describing the various error states.
     */
    List<String> getErrors(){
        return this.currentErrors.values().stream()
                .filter(errorMessage -> errorMessage.isErrorSet() == true)
                .map(ErrorMessage::getMessage)
                .collect(Collectors.toList());
    }
    
    /**
     * Represents an error message for the CommonFilesSearch panel, it's 
     * uniqueId, and it's status.
     */
    private class ErrorMessage {
        
        private final String message;
        private boolean status;
        
        /**
         * Create a message with a unique uniqueId.  Default status is false (off).
         * @param uniqueId unique uniqueId
         * @param message message to display
         */
        ErrorMessage(String message){
            this.message = message;
            this.status = false;
        }
        
        /**
         * Update the status of this message
         * @param status 
         */
        void setStatus(boolean status){
            this.status = status;
        }
        
        /**
         * Return the message 
         * @return 
         */
        String getMessage(){
            return this.message;
        }
        
        /**
         * Return the status (true for error status, false for no error)
         * @return 
         */
        boolean isErrorSet(){
            return this.status;
        }
    }
}

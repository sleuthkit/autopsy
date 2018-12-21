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
package org.sleuthkit.autopsy.centralrepository.datamodel;

/**
 * Exception to denote that the Central Repo is not compatable with the current version of the software.
 */
public class IncompatibleCentralRepoException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Construct an exception with the given message.
     * @param message error message
     */
    public IncompatibleCentralRepoException(String message){
        super(message);
    }
    
    /**
     * Construct an exception with the given message and inner exception.
     * @param message error message
     * @param cause inner exception
     */
    public IncompatibleCentralRepoException(String message, Throwable cause){
        super(message, cause);
    }
    
    /**
     * Construct an exception with the given inner exception.
     * @param cause inner exception
     */
    public IncompatibleCentralRepoException(Throwable cause){
        super(cause);
    }    
}

/*
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
package org.sleuthkit.autopsy.filerepository;

import org.openide.modules.ModuleInstall;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.datamodel.filerepository.FileRepository;
import org.sleuthkit.datamodel.filerepository.FileRepositorySettings;

public class Installer extends ModuleInstall {

    private static final long serialVersionUID = 1L;

    private static Installer instance;

    public synchronized static Installer getDefault() {
        if (instance == null) {
            instance = new Installer();
        }
        return instance;
    }

    private Installer() {
        super();
    }
    
    @Override
    public void restored() {
        
        FileRepository.setErrorHandler(new FileRepositoryUtils.AutopsyFileRepositoryErrorHandler());
        if (UserPreferences.getFileRepositoryEnabled()) {            
            // Initialize the file repository settings
            FileRepository.initialize(new FileRepositorySettings(UserPreferences.getFileRepositoryAddress(),
                UserPreferences.getFileRepositoryPort()));
        }
    }
    
    @Override
    public void close() {
        FileRepository.deinitialize();
    }
}
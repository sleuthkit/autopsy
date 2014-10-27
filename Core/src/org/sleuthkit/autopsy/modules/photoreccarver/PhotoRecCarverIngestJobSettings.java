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
package org.sleuthkit.autopsy.modules.photoreccarver;

import java.io.File;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest job settings for the Bulk Extractor ingest module.
 */
final class PhotoRecCarverIngestJobSettings implements IngestModuleIngestJobSettings
{

    private static final long serialVersionUID = 1L;
    public static final String PHOTOREC_DIRECTORY = "photorec_exec/"; //NON-NLS
    public static final String PHOTOREC_EXECUTABLE = "photorec_win.exe"; //NON-NLS
    public static final String LOG_FILE = "run_log.txt"; //NON-NLS
    private File executableFile;
    
    /**
     * Constructs serializable ingest job settings for the Unallocated Carver ingest module.
     *
     * @param None
     */
    PhotoRecCarverIngestJobSettings()
    {
        try
        {
            String execName = PHOTOREC_DIRECTORY + PHOTOREC_EXECUTABLE;
            executableFile = locateExecutable(execName);
        }
        catch (IngestModule.IngestModuleException ex)
        {
            Exceptions.printStackTrace(ex);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public long getVersionNumber()
    {
        return PhotoRecCarverIngestJobSettings.serialVersionUID;
    }

    /**
     * Gets the File to execute
     *
     * @return The File to execute
     */
    public File executableFile()
    {
        return executableFile;
    }

    /**
     * Queries whether the carver is enabled. It is disabled if the executable is not found
     *
     * @return True if executable found, false otherwise
     */
    public boolean unallocatedCarverEnabled() throws IngestModule.IngestModuleException
    {
        return locateExecutable(executableFile.toString()).exists();
    }

    /**
     * Locates the PhotoRec executable.
     *
     * @return The path of the executable.
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    File locateExecutable(String executableToFindName) throws IngestModule.IngestModuleException
    {
        // Must be running under a Windows operating system.
        if (!PlatformUtil.isWindowsOS())
        {
            throw new IngestModule.IngestModuleException(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "unsupportedOS.message"));
        }

        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, PhotoRecCarverFileIngestModule.class
                .getPackage().getName(), false);
        if (null == exeFile)
        {
            throw new IngestModule.IngestModuleException(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "missingExecutable.message"));
        }

        if (!exeFile.canExecute())
        {
            throw new IngestModule.IngestModuleException(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "cannotRunExecutable.message"));
        }

        return exeFile;
    }
}

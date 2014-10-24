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
    public static final String LOG_FILE = "run_log_"; //NON-NLS
    public static final String LOG_FILE_EXT = ".txt"; //NON-NLS
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

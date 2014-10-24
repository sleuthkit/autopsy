package org.sleuthkit.autopsy.modules.photoreccarver;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;

/**
 * A factory for creating instances of file ingest modules that carve unallocated space
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class PhotoRecCarverIngestModuleFactory extends IngestModuleFactoryAdapter
{

    private static final String VERSION_NUMBER = "1.0";

    /**
     * Gets the ingest module name for use within this package.
     *
     * @return A name string.
     */
    static String getModuleName()
    {
        return NbBundle.getMessage(PhotoRecCarverIngestModuleFactory.class, "moduleDisplayName.text");
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleDisplayName()
    {
        return PhotoRecCarverIngestModuleFactory.getModuleName();
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleDescription()
    {
        return NbBundle.getMessage(PhotoRecCarverIngestModuleFactory.class, "moduleDescription.text");
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleVersionNumber()
    {
        return PhotoRecCarverIngestModuleFactory.VERSION_NUMBER;
    }

    /**
     * @inheritDoc
     */
    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings()
    {
        return new PhotoRecCarverIngestJobSettings();
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean hasIngestJobSettingsPanel()
    {
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings)
    {
        if (!(settings instanceof PhotoRecCarverIngestJobSettings))
        {
            throw new IllegalArgumentException(NbBundle.getMessage(PhotoRecCarverIngestModuleFactory.class, "unrecognizedSettings.message"));
        }
        return new PhotoRecCarverIngestJobSettingsPanel((PhotoRecCarverIngestJobSettings) settings);
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isFileIngestModuleFactory()
    {
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings)
    {
        if (!(settings instanceof PhotoRecCarverIngestJobSettings))
        {
            throw new IllegalArgumentException(NbBundle.getMessage(PhotoRecCarverIngestModuleFactory.class, "unrecognizedSettings.message"));
        }
        return new PhotoRecCarverFileIngestModule((PhotoRecCarverIngestJobSettings) settings);
    }

}

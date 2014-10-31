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

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
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
        return Version.getVersion();
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
        if (!(settings instanceof IngestModuleIngestJobSettings))
        {
            throw new IllegalArgumentException(NbBundle.getMessage(PhotoRecCarverIngestModuleFactory.class, "unrecognizedSettings.message"));
        }
        return new PhotoRecCarverFileIngestModule();
    }

}

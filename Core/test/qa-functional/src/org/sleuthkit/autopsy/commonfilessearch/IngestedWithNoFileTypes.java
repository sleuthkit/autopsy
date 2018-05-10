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
package org.sleuthkit.autopsy.commonfilessearch;

import java.util.ArrayList;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Ingested w/o mime type info added to DB.
 * 
 * Setup:
 *
 * Add images set 1, set 2, set 3, and set 4 to case.  Do not run mime type module.
 */
public class IngestedWithNoFileTypes extends IntraCaseCommonFilesSearchTest {

    public IngestedWithNoFileTypes(String name) {
        super(name);
    }

    @Override
    public void setUp() {
        super.setUp();
        
        IngestModuleTemplate hashLookupTemplate = IngestUtils.getIngestModuleTemplate(new HashLookupModuleFactory());

        ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(hashLookupTemplate);

        IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestedWithHashAlgOnly.class.getCanonicalName(), IngestJobSettings.IngestType.FILES_ONLY, templates);

        try {
            IngestUtils.runIngestJob(Case.getCurrentCaseThrows().getDataSources(), ingestJobSettings);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    @Override
    protected String getCaseName() {
        return "IngestedWithNoFileTypes";
    }

}

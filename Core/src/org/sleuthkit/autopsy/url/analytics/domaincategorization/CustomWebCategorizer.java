/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.url.analytics.domaincategorization;

import java.sql.SQLException;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.url.analytics.DomainCategorizer;
import org.sleuthkit.autopsy.url.analytics.DomainCategorizerException;
import org.sleuthkit.autopsy.url.analytics.DomainCategory;

/**
 * A DomainCategoryProvider for custom web categories. NOTE: If this class
 * package or name change, code in DomainCategoryRunner will also need to change
 * to reflect the changing class name for ordering purposes.
 */
@ServiceProvider(service = DomainCategorizer.class)
public class CustomWebCategorizer implements DomainCategorizer {

    private static final Logger logger = Logger.getLogger(CustomWebCategorizer.class.getName());

    private final WebCategoriesDataModel dataModel;

    /**
     * Constructor accepting a custom WebCategoriesDataModel.
     *
     * @param dataModel The WebCategoriesDataModel to use as a data model.
     */
    CustomWebCategorizer(WebCategoriesDataModel dataModel) {
        this.dataModel = dataModel;
    }

    /**
     * No parameter constructor that uses the singleton instance of the
     * WebCategoriesDataModel.
     *
     */
    public CustomWebCategorizer() {
        this(WebCategoriesDataModel.getInstance());
    }

    @Override
    public DomainCategory getCategory(String domain, String host) throws DomainCategorizerException {
        if (!dataModel.isInitialized()) {
            return null;
        }
        String hostToUse = (StringUtils.isBlank(host)) ? domain : host;
        if (StringUtils.isBlank(hostToUse)) {
            return null;
        }

        hostToUse = hostToUse.toLowerCase();

        try {
            return dataModel.getMatchingRecord(hostToUse);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "There was an error while retrieving data for: " + hostToUse, ex);
            return null;
        }
    }

    @Override
    public void initialize() throws DomainCategorizerException {
        try {
            dataModel.initialize();
        } catch (SQLException ex) {
            throw new DomainCategorizerException("Unable to initialize.", ex);
        }
    }

    @Override
    public void close() throws SQLException {
        dataModel.close();
    }
}

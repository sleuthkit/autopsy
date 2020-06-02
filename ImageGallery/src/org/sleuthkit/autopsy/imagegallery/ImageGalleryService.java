/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import java.util.ArrayList;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.appservices.AutopsyService;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagNameDefinition;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TagSet;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * An Autopsy service that creates/opens a local drawables database and
 * creates/updates the Image Gallery tables in the case database.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = AutopsyService.class)
})
@NbBundle.Messages({
    "ImageGalleryService.serviceName=Image Gallery Update Service"
})
public class ImageGalleryService implements AutopsyService {

    private static final String CATEGORY_ONE_NAME = "Child Exploitation (Illegal)";
    private static final String CATEGORY_TWO_NAME = "Child Exploitation (Non-Illegal/Age Difficult)";
    private static final String CATEGORY_THREE_NAME = "CGI/Animation (Child Exploitive)";
    private static final String CATEGORY_FOUR_NAME = "Exemplar/Comparison (Internal Use Only)";
    private static final String CATEGORY_FIVE_NAME = "Non-pertinent";

    private static final List<TagNameDefinition> DEFAULT_CATEGORY_DEFINITION = new ArrayList<>();

    static {
        DEFAULT_CATEGORY_DEFINITION.add(new TagNameDefinition(CATEGORY_ONE_NAME, "", TagName.HTML_COLOR.RED, TskData.FileKnown.BAD));
        DEFAULT_CATEGORY_DEFINITION.add(new TagNameDefinition(CATEGORY_TWO_NAME, "", TagName.HTML_COLOR.LIME, TskData.FileKnown.BAD));
        DEFAULT_CATEGORY_DEFINITION.add(new TagNameDefinition(CATEGORY_THREE_NAME, "", TagName.HTML_COLOR.YELLOW, TskData.FileKnown.BAD));
        DEFAULT_CATEGORY_DEFINITION.add(new TagNameDefinition(CATEGORY_FOUR_NAME, "", TagName.HTML_COLOR.PURPLE, TskData.FileKnown.UNKNOWN));
        DEFAULT_CATEGORY_DEFINITION.add(new TagNameDefinition(CATEGORY_FIVE_NAME, "", TagName.HTML_COLOR.FUCHSIA, TskData.FileKnown.UNKNOWN));
    }

    @Override
    public String getServiceName() {
        return Bundle.ImageGalleryService_serviceName();
    }

    /**
     * Creates an image gallery controller for the current case. As a result,
     * creates/opens a local drawables database and creates/updates the Image
     * Gallery tables in the case database.
     *
     * @param context The case context which includes things such as the case, a
     *                progress indicator for the operation, a cancellation
     *                request flag, etc.
     *
     * @throws
     * org.sleuthkit.autopsy.appservices.AutopsyService.AutopsyServiceException
     */
    @NbBundle.Messages({
        "ImageGalleryService.openCaseResources.progressMessage.start=Opening Image Gallery databases...",
        "ImageGalleryService.openCaseResources.progressMessage.finish=Opened Image Gallery databases.",})
    @Override
    public void openCaseResources(CaseContext context) throws AutopsyServiceException {
        if (context.cancelRequested()) {
            return;
        }
        ProgressIndicator progress = context.getProgressIndicator();
        progress.progress(Bundle.ImageGalleryService_openCaseResources_progressMessage_start());
        try {

            // Check to see if the Project VIC tag set exists, if not create a 
            // tag set using the default tags.
            boolean addDefaultTagSet = true;
            List<TagSet> tagSets = context.getCase().getServices().getTagsManager().getAllTagSets();
            for (TagSet set : tagSets) {
                if (set.getName().equals(ImageGalleryController.getCategoryTagSetName())) {
                    addDefaultTagSet = false;
                    break;
                }
            }

            if (addDefaultTagSet) {
                addDefaultTagSet(context.getCase());
            }

            ImageGalleryController.createController(context.getCase());
        } catch (TskCoreException ex) {
            throw new AutopsyServiceException("Error opening Image Gallery databases", ex);
        }
        progress.progress(Bundle.ImageGalleryService_openCaseResources_progressMessage_finish());
    }

    /**
     * Shuts down the image gallery controller for the current case. As a
     * result, closes the local drawables database.
     *
     * @param context The case context which includes things such as the case, a
     *                progress indicator for the operation, a cancellation
     *                request flag, etc.
     *
     * @throws
     * org.sleuthkit.autopsy.appservices.AutopsyService.AutopsyServiceException
     */
    @Override
    public void closeCaseResources(CaseContext context) throws AutopsyServiceException {
        ImageGalleryController.shutDownController(context.getCase());
    }

    /**
     * Add the default category tag set to the case db.
     *
     * @param skCase Currently open case.
     *
     * @throws TskCoreException
     */
    private void addDefaultTagSet(Case currentCase) throws TskCoreException {
        List<TagName> tagNames = new ArrayList<>();
        for (TagNameDefinition def : DEFAULT_CATEGORY_DEFINITION) {
            tagNames.add(currentCase.getSleuthkitCase().addOrUpdateTagName(def.getDisplayName(), def.getDescription(), def.getColor(), def.getKnownStatus()));
        }

        currentCase.getServices().getTagsManager().addTagSet(ImageGalleryController.getCategoryTagSetName(), tagNames);
    }

}

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

    /* Image Gallery has its own definition of Project VIC tag names because 
     * these will be used if the Project Vic module is not installed. These will
     * get added when a case is opened if the tag set is not already defined. 
     * 
     * The following list of names must be kept in sync with the CountryManager
     * code in the ProjectVic module.
     * 
     * Autopsy Core Tag code and TSK DataModel upgrade code also have a 
     * references to the "Projet VIC" set name. Be careful changing any of these names.
     */
    static String PROJECT_VIC_TAG_SET_NAME = "Project VIC";
    private static final String PV_US_CAT0 = "Non-Pertinent";
    private static final String PV_US_CAT1 = "Child Abuse Material - (CAM)";
    private static final String PV_US_CAT2 = "Child Exploitive (Non-CAM) Age Difficult";
    private static final String PV_US_CAT3 = "CGI/Animation - Child Exploitive";
    private static final String PV_US_CAT4 = "Comparison Images";
    
    private static final List<TagNameDefinition> PROJECT_VIC_US_CATEGORIES = new ArrayList<>();

    static {
        // NOTE: The colors here are what will be shown in the border
        PROJECT_VIC_US_CATEGORIES.add(new TagNameDefinition(PV_US_CAT0, "", TagName.HTML_COLOR.GREEN, TskData.TagType.SUSPICIOUS));
        PROJECT_VIC_US_CATEGORIES.add(new TagNameDefinition(PV_US_CAT1, "", TagName.HTML_COLOR.RED, TskData.TagType.BAD));
        PROJECT_VIC_US_CATEGORIES.add(new TagNameDefinition(PV_US_CAT2, "", TagName.HTML_COLOR.YELLOW, TskData.TagType.BAD));
        PROJECT_VIC_US_CATEGORIES.add(new TagNameDefinition(PV_US_CAT3, "", TagName.HTML_COLOR.FUCHSIA, TskData.TagType.BAD));
        PROJECT_VIC_US_CATEGORIES.add(new TagNameDefinition(PV_US_CAT4, "", TagName.HTML_COLOR.BLUE, TskData.TagType.SUSPICIOUS));
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
            boolean addProjVicTagSet = true;
            List<TagSet> tagSets = context.getCase().getServices().getTagsManager().getAllTagSets();
            for (TagSet set : tagSets) {
                if (set.getName().equals(PROJECT_VIC_TAG_SET_NAME)) {
                    addProjVicTagSet = false;
                    break;
                }
            }

            if (addProjVicTagSet) {
                addProjetVicTagSet(context.getCase());
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
    private void addProjetVicTagSet(Case currentCase) throws TskCoreException {
        List<TagName> tagNames = new ArrayList<>();
        for (TagNameDefinition def : PROJECT_VIC_US_CATEGORIES) {
            tagNames.add(currentCase.getSleuthkitCase().getTaggingManager().addOrUpdateTagName(def.getDisplayName(), def.getDescription(), def.getColor(), def.getTagType()));
        }
        currentCase.getServices().getTagsManager().addTagSet(PROJECT_VIC_TAG_SET_NAME, tagNames);
    }
}

/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors;

import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.framework.AutopsyService;

@ServiceProviders(value = {@ServiceProvider(service = AutopsyService.class)})

public class ImageWriterService implements AutopsyService {

    @Override
    public String getServiceName() {
        return NbBundle.getMessage(this.getClass(), "ImageWriterService.serviceName");
    }
    
    @Override
    public void closeCaseResources(CaseContext context) throws AutopsyServiceException {
        context.getProgressIndicator().progress("Waiting for VHD(s) to complete");
        
        if(ImageWriter.jobsAreInProgress()){
            NotifyDescriptor descriptor = new NotifyDescriptor.Confirmation(
                    "Wait for Image Writer to finish?",
                    "Title",
                    NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.WARNING_MESSAGE);
            descriptor.setValue(NotifyDescriptor.NO_OPTION);
            Object response = DialogDisplayer.getDefault().notify(descriptor);

            if(response == DialogDescriptor.NO_OPTION){
                ImageWriter.cancelAllJobs();
            }

            // Wait for all finishImage jobs to complete. If the jobs got cancelled
            // this will be very fast.
            ImageWriter.waitForAllJobsToFinish();
        }
    }
}

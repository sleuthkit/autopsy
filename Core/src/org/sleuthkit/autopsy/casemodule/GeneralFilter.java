/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.swing.filechooser.FileFilter;
import org.openide.util.NbBundle;

/**
 * FileFilter helper class. Matches files based on extension
 */
public class GeneralFilter extends FileFilter {

    // Extensions & Descriptions for commonly used filters
    public static final List<String> RAW_IMAGE_EXTS = Arrays.asList(".img", ".dd", ".001", ".aa", ".raw", ".bin"); //NON-NLS
    @NbBundle.Messages("GeneralFilter.rawImageDesc.text=Raw Images (*.img, *.dd, *.001, *.aa, *.raw, *.bin)")
    public static final String RAW_IMAGE_DESC = Bundle.GeneralFilter_rawImageDesc_text();

    public static final List<String> ENCASE_IMAGE_EXTS = Arrays.asList(".e01"); //NON-NLS
    @NbBundle.Messages("GeneralFilter.encaseImageDesc.text=Encase Images (*.e01)")
    public static final String ENCASE_IMAGE_DESC = Bundle.GeneralFilter_encaseImageDesc_text();

    public static final List<String> VIRTUAL_MACHINE_EXTS = Arrays.asList(".vmdk", ".vhd"); //NON-NLS
    @NbBundle.Messages("GeneralFilter.virtualMachineImageDesc.text=Virtual Machines (*.vmdk, *.vhd)")
    public static final String VIRTUAL_MACHINE_DESC = Bundle.GeneralFilter_virtualMachineImageDesc_text();

    public static final List<String> EXECUTABLE_EXTS = Arrays.asList(".exe"); //NON-NLS
    @NbBundle.Messages("GeneralFilter.executableDesc.text=Executables (*.exe)")
    public static final String EXECUTABLE_DESC = Bundle.GeneralFilter_executableDesc_text();

    public static final List<String> GRAPHIC_IMAGE_EXTS = Arrays.asList(".png", ".jpeg", ".jpg", ".gif", ".bmp"); //NON-NLS
    @NbBundle.Messages("GeneralFilter.graphicImageDesc.text=Images (*.png, *.jpg, *.jpeg, *.gif, *.bmp)")
    public static final String GRAPHIC_IMG_DECR = Bundle.GeneralFilter_graphicImageDesc_text();

    private final List<String> extensions;
    private final String desc;

    public GeneralFilter(List<String> ext, String desc) {
        super();
        this.extensions = ext;
        this.desc = desc;
    }

    /**
     * Checks whether the given file is accepted by this filter.
     *
     * @param f the given file
     *
     * @return boolean return true if accepted, false otherwise
     */
    @Override
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        } else {
            String name = f.getName().toLowerCase();
            return extensions.stream().anyMatch(name::endsWith);
        }
    }

    /**
     * Returns the description of this file filter
     *
     * @return desc return the description
     */
    @Override
    public String getDescription() {
        return desc;
    }
}

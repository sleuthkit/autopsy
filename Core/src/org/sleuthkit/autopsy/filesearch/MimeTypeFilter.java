/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.filesearch;

import java.awt.event.ActionListener;
import org.openide.util.NbBundle.Messages;

/**
 * Filter by mime type used in filter areas of file search by attribute.
 */
class MimeTypeFilter extends AbstractFileSearchFilter<MimeTypePanel> {

    public MimeTypeFilter(MimeTypePanel component) {
        super(component);
    }

    public MimeTypeFilter() {
        this(new MimeTypePanel());
    }

    @Override
    public boolean isEnabled() {
        return this.getComponent().isSelected();
    }

    @Override
    public String getPredicate() throws FilterValidationException {
        String predicate = "";
        for (String mimeType : this.getComponent().getMimeTypesSelected()) {
            predicate += "mime_type = '" + mimeType + "' OR ";
        }
        if (predicate.length() > 3) {
            predicate = predicate.substring(0, predicate.length() - 3);
        }
        return predicate;
    }

    @Override
    public void addActionListener(ActionListener l) {
    }

    @Override
    @Messages ({
        "MimeTypeFilter.errorMessage.emptyMimeType=At least one MIME type must be selected."
    })
    public boolean isValid() {
        if(this.getComponent().getMimeTypesSelected().isEmpty()){
            setLastError(Bundle.MimeTypeFilter_errorMessage_emptyMimeType());
            return false;
        }
        return true;
    }
}

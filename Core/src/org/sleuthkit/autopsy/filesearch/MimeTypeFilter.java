/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.filesearch;

import java.awt.event.ActionListener;

/**
 *
 * @author oliver
 */
class MimeTypeFilter extends AbstractFileSearchFilter<MimeTypePanel>  {

    public MimeTypeFilter(MimeTypePanel component) {
        super(component);
    }
    public MimeTypeFilter() {
        this(new MimeTypePanel());
    }

    @Override
    public boolean isEnabled() {
        return this.getComponent().isSelected() &&
                !this.getComponent().getMimeTypesSelected().isEmpty();
    }

    @Override
    public String getPredicate() throws FilterValidationException {
        String predicate = "";
        for(String mimeType : this.getComponent().getMimeTypesSelected()) {
            predicate += "mime_type = '" + mimeType + "' OR ";
        }
        if(predicate.length() > 3) {
            predicate = predicate.substring(0, predicate.length() - 3);
        }
        return predicate;
    }

    @Override
    public void addActionListener(ActionListener l) {
    }
    
}

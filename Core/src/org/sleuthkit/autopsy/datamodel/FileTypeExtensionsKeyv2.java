/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.util.Objects;
import org.sleuthkit.autopsy.datamodel.ThreePanelViewsDAO.SearchFilterInterface;

/**
 *
 * @author gregd
 */
public class FileTypeExtensionsKeyv2 {
    private final SearchFilterInterface filter;
    private final Long dataSourceId;
    private final boolean knownShown;

    // this assumes that filters implicitly or explicitly implement hashCode and equals to work
    public FileTypeExtensionsKeyv2(SearchFilterInterface filter, Long dataSourceId, boolean showKnown) {
        this.filter = filter;
        this.dataSourceId = dataSourceId;
        this.knownShown = showKnown;
    }

    public SearchFilterInterface getFilter() {
        return filter;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public boolean isKnownShown() {
        return knownShown;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.filter);
        hash = 23 * hash + Objects.hashCode(this.dataSourceId);
        hash = 23 * hash + (this.knownShown ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FileTypeExtensionsKeyv2 other = (FileTypeExtensionsKeyv2) obj;
        if (this.knownShown != other.knownShown) {
            return false;
        }
        if (!Objects.equals(this.filter, other.filter)) {
            return false;
        }
        if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
            return false;
        }
        return true;
    }
    
    
}

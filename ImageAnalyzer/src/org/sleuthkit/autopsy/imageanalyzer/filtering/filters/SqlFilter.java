/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.imageanalyzer.filtering.filters;

import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;

/**
 *
 *
 */
public interface SqlFilter {

    public Boolean accept(DrawableFile df);

    public String getFilterQueryString();
}

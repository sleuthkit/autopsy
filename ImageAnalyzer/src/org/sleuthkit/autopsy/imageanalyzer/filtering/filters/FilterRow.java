/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.imageanalyzer.filtering.filters;

import org.sleuthkit.autopsy.imageanalyzer.filtering.filters.AbstractFilter;
import javafx.scene.layout.AnchorPane;

/**
 *
 * @author jmillman
 */
public abstract class FilterRow<T extends AbstractFilter> extends AnchorPane {

    public abstract T getFilter();
}

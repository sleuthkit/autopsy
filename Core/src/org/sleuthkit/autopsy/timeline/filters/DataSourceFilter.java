/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

/**
 *
 */
public class DataSourceFilter extends AbstractFilter {

    private final String dataSourceName;
    private final long dataSourceID;

    public long getDataSourceID() {
        return dataSourceID;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public DataSourceFilter(String dataSourceName, long dataSourceID) {
        this.dataSourceName = dataSourceName;
        this.dataSourceID = dataSourceID;
    }

    @Override
    synchronized public DataSourceFilter copyOf() {
        DataSourceFilter filterCopy = new DataSourceFilter(getDisplayName(), getDataSourceID());
        filterCopy.setActive(isActive());
        filterCopy.setDisabled(isDisabled());
        return filterCopy;
    }

    @Override
    public String getDisplayName() {
        return dataSourceName;
    }

    @Override
    public String getHTMLReportString() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.commonfilesearch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.centralrepository.datamodel.InstanceTableCallback;

/**
 *
 * @author Andrrew
 */
public class EamDbAttributeInstanceValuesCallback implements InstanceTableCallback {

        List<String> correlationValues = new ArrayList<>();
        
        @Override
        public void process(ResultSet resultSet) {
            try {
                while(resultSet.next()){
                    correlationValues.add(InstanceTableCallback.getValue(resultSet));
                }
            } catch (SQLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        public List<String> getCorrelationValues() {
            return Collections.unmodifiableList(correlationValues);
        }


    }
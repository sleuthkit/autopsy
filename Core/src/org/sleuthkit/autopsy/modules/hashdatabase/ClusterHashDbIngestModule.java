/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.hashdatabase;

import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author ekohlenb
 */
public class ClusterHashDbIngestModule extends HashDbIngestModule{
    public ClusterHashDbIngestModule(HashLookupModuleSettings settings)
    {
        super(settings);
    }

    public ClusterHashDbIngestModule(HashLookupModuleSettings settings, SleuthkitCase skCase) {
        super(settings);
        this.skCase = skCase;
    }
}

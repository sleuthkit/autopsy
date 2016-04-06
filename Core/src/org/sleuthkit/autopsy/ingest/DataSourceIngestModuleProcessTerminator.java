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
package org.sleuthkit.autopsy.ingest;

import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.ExecUtil.ProcessTerminator;
import org.sleuthkit.autopsy.coreutils.ExecUtil.TimedProcessTerminator;

/**
 * A process terminator for data source ingest modules that checks for ingest
 * job cancellation and optionally checks for process run time in excess of a
 * specified maximum.
 */
public final class DataSourceIngestModuleProcessTerminator implements ProcessTerminator {

    private final IngestJobContext context;
    private TimedProcessTerminator timedTerminator;
    private ProcTerminationCode terminationCode;

    /**
     * Constructs a process terminator for a data source ingest module.
     *
     * @param context The ingest job context for the ingest module.
     */
    public DataSourceIngestModuleProcessTerminator(IngestJobContext context) {
        this.context = context;
        this.terminationCode = ProcTerminationCode.NONE;
    }

    /**
     * Constructs a process terminator for a data source ingest module.
     *
     * @param context             The ingest job context for the ingest module.
     * @param maxRunTimeInSeconds Maximum allowable run time of process.
     */
    public DataSourceIngestModuleProcessTerminator(IngestJobContext context, long maxRunTimeInSeconds) {
        this(context);
        this.timedTerminator = new TimedProcessTerminator(maxRunTimeInSeconds);
    }

    /**
     * Constructs a process terminator for a data source ingest module. Adds
     * ability to use global process termination time out.
     *
     * @param context          The ingest job context for the ingest module.
     * @param useGlobalTimeOut Flag whether to use global process termination
     *                         timeout.
     */
    public DataSourceIngestModuleProcessTerminator(IngestJobContext context, boolean useGlobalTimeOut) {
        this(context);
        if (useGlobalTimeOut) {
            this.timedTerminator = new ExecUtil.TimedProcessTerminator();
        }
    }

    @Override
    public boolean shouldTerminateProcess() {

        if (this.context.dataSourceIngestIsCancelled()) {
            this.terminationCode = ProcTerminationCode.CANCELATION;
            return true;
        }

        if (this.timedTerminator != null && this.timedTerminator.shouldTerminateProcess()) {
            this.terminationCode = ProcTerminationCode.TIME_OUT;
            return true;
        }

        return false;
    }

    /**
     * Returns process termination code.
     *
     * @return ProcTerminationCode Process termination code.
     */
    public ProcTerminationCode getTerminationCode() {
        return this.terminationCode;
    }
}

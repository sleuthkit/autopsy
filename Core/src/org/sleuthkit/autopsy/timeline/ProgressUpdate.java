/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.sleuthkit.autopsy.timeline;

import javax.annotation.concurrent.Immutable;

/**
 * bundles up progress information to be shown in the progress dialog
 */
@Immutable
public class ProgressUpdate {
    private final int progress;
    private final int total;
    private final String headerMessage;
    private final String detailMessage;

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    public String getHeaderMessage() {
        return headerMessage;
    }

    public String getDetailMessage() {
        return detailMessage;
    }

    public ProgressUpdate(int progress, int total, String headerMessage, String detailMessage) {
        this.progress = progress;
        this.total = total;
        this.headerMessage = headerMessage;
        this.detailMessage = detailMessage;
    }

    public ProgressUpdate(int progress, int total, String headerMessage) {
        this(progress, total, headerMessage, "");
    }

}

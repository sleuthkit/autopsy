/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * JLabel that shows ingest is running.
 */
@Messages({
    "IngestRunningLabel_defaultMessage=Ingest is currently running."
})
public class IngestRunningLabel extends JPanel {

    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_MESSAGE = Bundle.IngestRunningLabel_defaultMessage();
    private static final URL DEFAULT_ICON = IngestRunningLabel.class.getResource("/org/sleuthkit/autopsy/modules/filetypeid/warning16.png");

    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(
            IngestManager.IngestJobEvent.STARTED,
            IngestManager.IngestJobEvent.CANCELLED,
            IngestManager.IngestJobEvent.COMPLETED
    );

    private static Set<IngestRunningLabel> activeLabels = new HashSet<>();
    private static PropertyChangeListener classListener = null;
    private static Object lockObject = new Object();

    /**
     * Setup ingest event listener for the current label.
     *
     * @param label The label.
     */
    private static void setupListener(IngestRunningLabel label) {
        synchronized (lockObject) {

            // if listener is not initialized, initialize it.
            if (classListener == null) {
                classListener = (evt) -> {
                    if (evt == null) {
                        return;
                    }

                    if (evt.getPropertyName().equals(IngestManager.IngestJobEvent.STARTED.toString())) {
                        // ingest started
                        notifyListeners(true);

                    } else if (evt.getPropertyName().equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                            || evt.getPropertyName().equals(IngestManager.IngestJobEvent.COMPLETED.toString())) {
                        // ingest cancelled or finished
                        notifyListeners(false);

                    }
                };
                IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, classListener);
            }

            // add the item to the set
            activeLabels.add(label);
        }
    }

    /**
     * Notifies all listening instances of an update in ingest state.
     *
     * @param ingestIsRunning Whether or not ingest is running currently.
     */
    private static void notifyListeners(boolean ingestIsRunning) {
        synchronized (lockObject) {
            for (IngestRunningLabel label : activeLabels) {
                label.refreshState(ingestIsRunning);
            }
        }
    }

    /**
     * Removes a label from listening events.
     *
     * @param label The label to remove from listening events.
     */
    private static void removeListener(IngestRunningLabel label) {
        synchronized (lockObject) {
            activeLabels.remove(label);
            if (activeLabels.isEmpty() && classListener != null) {
                IngestManager.getInstance().removeIngestJobEventListener(classListener);
                classListener = null;
            }
        }
    }

    /**
     * Main constructor with default message and showing icon.
     */
    public IngestRunningLabel() {
        this(DEFAULT_MESSAGE, true);
    }

    /**
     * Constructor.
     *
     * @param message         The message to be shown.
     * @param showWarningIcon Whether or not to show warning icon.
     */
    public IngestRunningLabel(String message, boolean showWarningIcon) {
        JLabel jlabel = new JLabel();
        jlabel.setText(message);

        if (showWarningIcon) {
            jlabel.setIcon(new ImageIcon(DEFAULT_ICON));
        }

        setLayout(new BorderLayout());
        add(jlabel, BorderLayout.NORTH);

        setupListener(this);
        refreshState();
    }

    /**
     * Refresh state of this label based on ingest status.
     */
    protected final void refreshState() {
        refreshState(IngestManager.getInstance().isIngestRunning());
    }

    /**
     * Refresh state of this label based on ingest status.
     *
     * @param ingestIsRunning True if ingest is running.
     */
    protected final void refreshState(boolean ingestIsRunning) {
        setVisible(ingestIsRunning);
    }

    /**
     * Unregister this instance from listening for ingest status changes.
     */
    public void unregister() {
        removeListener(this);
    }
}

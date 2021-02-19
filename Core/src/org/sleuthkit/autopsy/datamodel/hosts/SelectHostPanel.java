/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.hosts;

import java.awt.Dialog;
import java.util.Objects;
import java.util.Vector;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.DefaultComboBoxModel;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to be displayed as a part of the add datasource wizard. Provides the
 * ability to select current host.
 */
public class SelectHostPanel extends javax.swing.JPanel {

    /**
     * A combo box item for a host (or null for default).
     */
    @Messages({
        "SelectHostPanel_HostCbItem_defaultHost=Default"
    })
    private static class HostCbItem {

        private final Host host;

        /**
         * Main constructor.
         *
         * @param host The host.
         */
        HostCbItem(Host host) {
            this.host = host;
        }

        /**
         * @return The host.
         */
        Host getHost() {
            return host;
        }

        @Override
        public String toString() {
            if (host == null) {
                return Bundle.SelectHostPanel_HostCbItem_defaultHost();
            } else if (host.getName() == null) {
                return "";
            } else {
                return host.getName();
            }
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + Objects.hashCode(this.host == null ? 0 : this.host.getId());
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
            final HostCbItem other = (HostCbItem) obj;
            if (!Objects.equals(
                    this.host == null ? 0 : this.host.getId(),
                    other.host == null ? 0 : other.host.getId())) {

                return false;
            }
            return true;
        }

    }

    private static final Logger logger = Logger.getLogger(SelectHostPanel.class.getName());

    /**
     * Creates new form SelectHostPanel
     */
    public SelectHostPanel() {
        initComponents();
        loadHostData();
        this.comboBoxHostName.addItem(new HostCbItem(null));
    }

    /**
     * @return The currently selected host or null if no selection.
     */
    public Host getSelectedHost() {
        return comboBoxHostName.getSelectedItem() instanceof HostCbItem
                ? ((HostCbItem) comboBoxHostName.getSelectedItem()).getHost()
                : null;
    }

    /**
     * Loads hosts from database and displays in combo box.
     */
    private void loadHostData() {
        Stream<HostCbItem> itemsStream;
        try {
            itemsStream = Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().getHosts().stream()
                    .filter(h -> h != null)
                    .sorted((a, b) -> getNameOrEmpty(a).compareToIgnoreCase(getNameOrEmpty(b)))
                    .map((h) -> new HostCbItem(h));

            Vector<HostCbItem> hosts = Stream.concat(Stream.of(new HostCbItem(null)), itemsStream)
                    .collect(Collectors.toCollection(Vector::new));

            comboBoxHostName.setModel(new DefaultComboBoxModel<>(hosts));
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to display host items with no current case.", ex);
        }
    }

    /**
     * Returns the name of the host or an empty string if the host or host name
     * is null.
     *
     * @param host The host.
     * @return The host name or empty string.
     */
    private String getNameOrEmpty(Host host) {
        return host == null || host.getName() == null ? "" : host.getName();
    }

    /**
     * Sets the selected host in the combo box with the specified host id. If
     * host id is null or host id is not found in list, 'default' will be
     * selected.
     *
     * @param hostId The host id.
     */
    private void setSelectedHostById(Long hostId) {
        int itemCount = comboBoxHostName.getItemCount();
        for (int i = 0; i < itemCount; i++) {
            HostCbItem curItem = comboBoxHostName.getItemAt(i);
            if (curItem == null) {
                continue;
            }

            Long curId = curItem.getHost() == null ? null : curItem.getHost().getId();
            if (curId == hostId) {
                comboBoxHostName.setSelectedIndex(i);
                return;
            }
        }

        // set to first item which should be 'Default'
        comboBoxHostName.setSelectedIndex(0);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.ButtonGroup radioButtonGroup = new javax.swing.ButtonGroup();
        generateNewRadio = new javax.swing.JRadioButton();
        specifyNewHostRadio = new javax.swing.JRadioButton();
        specifyNewHostTextField = new javax.swing.JTextField();
        useExistingHostRadio = new javax.swing.JRadioButton();
        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        existingHostList = new javax.swing.JList<>();

        radioButtonGroup.add(generateNewRadio);
        generateNewRadio.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(generateNewRadio, org.openide.util.NbBundle.getMessage(SelectHostPanel.class, "SelectHostPanel.generateNewRadio.text")); // NOI18N

        radioButtonGroup.add(specifyNewHostRadio);
        org.openide.awt.Mnemonics.setLocalizedText(specifyNewHostRadio, org.openide.util.NbBundle.getMessage(SelectHostPanel.class, "SelectHostPanel.specifyNewHostRadio.text")); // NOI18N

        specifyNewHostTextField.setText(org.openide.util.NbBundle.getMessage(SelectHostPanel.class, "SelectHostPanel.specifyNewHostTextField.text")); // NOI18N

        radioButtonGroup.add(useExistingHostRadio);
        org.openide.awt.Mnemonics.setLocalizedText(useExistingHostRadio, org.openide.util.NbBundle.getMessage(SelectHostPanel.class, "SelectHostPanel.useExistingHostRadio.text")); // NOI18N

        existingHostList.setModel(new javax.swing.AbstractListModel<HostCbItem>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jScrollPane1.setViewportView(existingHostList);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(generateNewRadio)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(specifyNewHostRadio)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(specifyNewHostTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 210, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(useExistingHostRadio)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 272, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(33, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(generateNewRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(specifyNewHostRadio)
                    .addComponent(specifyNewHostTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(useExistingHostRadio)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(56, Short.MAX_VALUE))
        );

        getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(SelectHostPanel.class, "SelectHostPanel.title")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<HostCbItem> existingHostList;
    private javax.swing.JRadioButton generateNewRadio;
    private javax.swing.JRadioButton specifyNewHostRadio;
    private javax.swing.JTextField specifyNewHostTextField;
    private javax.swing.JRadioButton useExistingHostRadio;
    // End of variables declaration//GEN-END:variables
}

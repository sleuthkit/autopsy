/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.communications.relationships;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAccount;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsDialog;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsDialogCallback;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsMode;
import org.sleuthkit.autopsy.centralrepository.persona.PersonaDetailsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;

/**
 * Panel to show the Personas for a given account. That is apart SummaryViewer.
 */
public final class SummaryPersonaPane extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private final static Logger logger = Logger.getLogger(SummaryPersonaPane.class.getName());

    private final Map<Component, Persona> personaMap;
    private final ViewButtonHandler viewButtonHandler = new ViewButtonHandler();
    private CentralRepoAccount currentCRAccount = null;
    private Account currentAccount = null;

    /**
     * Creates new form SummaryPersonaPane
     */
    SummaryPersonaPane() {
        initComponents();
        personaScrollPane.setViewportView(new JPanel());

        personaMap = new HashMap<>();
    }

    /**
     * Clear the persona list.
     */
    void clearList() {
        personaScrollPane.setViewportView(new JPanel());
        personaMap.clear();
    }

    /**
     * Show the message panel.
     */
    void showMessagePanel() {
        CardLayout layout = (CardLayout) getLayout();
        layout.show(this, "message");
    }

    /**
     * Set the message that appears when the message panel is visible.
     *
     * @param message Message to show.
     */
    void setMessage(String message) {
        messageLabel.setText(message);
    }

    /**
     * Update the list of personas to the new given list.
     *
     * @param personaList New list of personas to show
     */
    void updatePersonaList(Account account, CentralRepoAccount crAccount, List<Persona> personaList) {
        JPanel panel = new JPanel();
        currentCRAccount = crAccount;
        currentAccount = account;

        CardLayout layout = (CardLayout) getLayout();
        if (personaList.isEmpty()) {
            layout.show(this, "create");
        } else {
            panel.setLayout(new GridLayout(personaList.size() + 1, 1));
            int maxWidth = 0;
            List<PersonaPanel> panelList = new ArrayList<>();
            for (Persona persona : personaList) {
                PersonaPanel personaPanel = new PersonaPanel(persona);
                JButton viewButton = personaPanel.getViewButton();
                panel.add(personaPanel);

                personaMap.put(viewButton, persona);
                viewButton.addActionListener(viewButtonHandler);

                panelList.add(personaPanel);
                maxWidth = Math.max(personaPanel.getPersonaLabelPreferedWidth(), maxWidth);
            }

            //Set the preferred width of the labeles to the buttons line up.
            if (panelList.size() > 1) {
                for (PersonaPanel ppanel : panelList) {
                    ppanel.setPersonalLabelPreferredWidth(maxWidth);
                }
            }

            panel.add(Box.createVerticalGlue());
            personaScrollPane.setViewportView(panel);
            layout.show(this, "persona");
        }

    }

    /**
     * ActionListener to handle the launching of the view dialog for the given
     * persona.
     */
    final private class ViewButtonHandler implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            Persona persona = personaMap.get((Component) e.getSource());
            new PersonaDetailsDialog(SummaryPersonaPane.this,
                    PersonaDetailsMode.VIEW, persona, new PersonaViewCallbackImpl());
        }
    }

    /**
     * Callback method for the view mode of the PersonaDetailsDialog
     */
    private final class PersonaViewCallbackImpl implements PersonaDetailsDialogCallback {

        @Override
        public void callback(Persona persona) {
            // nothing to do 
        }
    }

    /**
     * Callback class to handle the creation of a new person for the given
     * account
     */
    private final class PersonaCreateCallbackImpl implements PersonaDetailsDialogCallback {

        @Override
        public void callback(Persona persona) {
            if (persona != null) {
                List<Persona> list = new ArrayList<>();
                list.add(persona);

                CentralRepoAccount crAccount = null;
                Collection<PersonaAccount> personaAccounts = null;
                try {
                    personaAccounts = persona.getPersonaAccounts();
                } catch (CentralRepoException ex) {
                    logger.log(Level.WARNING, String.format("Failed to get cr account from person %s (%d)", persona.getName(), persona.getId()), ex);
                }

                if (personaAccounts != null) {
                    Iterator<PersonaAccount> iterator = personaAccounts.iterator();
                    if (iterator.hasNext()) {
                        crAccount = iterator.next().getAccount();
                    }
                }

                updatePersonaList(currentAccount, crAccount, list);
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        personaScrollPane = new javax.swing.JScrollPane();
        messagePane = new javax.swing.JPanel();
        messageLabel = new javax.swing.JLabel();
        createPersonaPanel = new javax.swing.JPanel();
        noPersonaLabel = new javax.swing.JLabel();
        createButton = new javax.swing.JButton();

        setLayout(new java.awt.CardLayout());

        personaScrollPane.setBorder(null);
        add(personaScrollPane, "persona");

        messagePane.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(messageLabel, org.openide.util.NbBundle.getMessage(SummaryPersonaPane.class, "SummaryPersonaPane.messageLabel.text")); // NOI18N
        messageLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weighty = 1.0;
        messagePane.add(messageLabel, gridBagConstraints);

        add(messagePane, "message");

        createPersonaPanel.setPreferredSize(new java.awt.Dimension(200, 100));
        createPersonaPanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(noPersonaLabel, org.openide.util.NbBundle.getMessage(SummaryPersonaPane.class, "SummaryPersonaPane.noPersonaLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(7, 5, 0, 0);
        createPersonaPanel.add(noPersonaLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(createButton, org.openide.util.NbBundle.getMessage(SummaryPersonaPane.class, "SummaryPersonaPane.createButton.text")); // NOI18N
        createButton.setMargin(new java.awt.Insets(0, 5, 0, 5));
        createButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        createPersonaPanel.add(createButton, gridBagConstraints);

        add(createPersonaPanel, "create");
    }// </editor-fold>//GEN-END:initComponents

    @NbBundle.Messages({
        "# {0} - accountIdentifer",
        "SummaryPersonaPane_not_account_in_cr=Unable to find an account with identifier {0} in the Central Repository."
    })
    
    private void createButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createButtonActionPerformed
        PersonaDetailsDialog createPersonaDialog = new PersonaDetailsDialog(SummaryPersonaPane.this,
                PersonaDetailsMode.CREATE, null, new PersonaCreateCallbackImpl(), false);

        // Pre populate the persona name and accounts if we have them.
        PersonaDetailsPanel personaPanel = createPersonaDialog.getDetailsPanel();
        if (currentCRAccount != null) {
            personaPanel.addAccount(currentCRAccount, "", Persona.Confidence.HIGH);

        } else {
            createPersonaDialog.setStartupPopupMessage(Bundle.SummaryPersonaPane_not_account_in_cr(currentAccount.getTypeSpecificID()));
        }
        personaPanel.setPersonaName(currentAccount.getTypeSpecificID());
        // display the dialog now
        createPersonaDialog.display();
    }//GEN-LAST:event_createButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton createButton;
    private javax.swing.JPanel createPersonaPanel;
    private javax.swing.JLabel messageLabel;
    private javax.swing.JPanel messagePane;
    private javax.swing.JLabel noPersonaLabel;
    private javax.swing.JScrollPane personaScrollPane;
    // End of variables declaration//GEN-END:variables
}

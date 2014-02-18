/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.hashdatabase;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.Lookup;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.ingest.IngestConfigurator;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import static org.sleuthkit.autopsy.hashdatabase.HashDbManager.HashDb;

/**
 * Instances of this Action allow users to content to a hash database.
 */
final class AddContentToHashDbAction extends AbstractAction implements Presenter.Popup { 
    private static AddContentToHashDbAction instance;
    private final static String SINGLE_SELECTION_NAME = NbBundle.getMessage(AddContentToHashDbAction.class,
                                                                            "AddContentToHashDbAction.singleSelectionName");
    private final static String MULTIPLE_SELECTION_NAME = NbBundle.getMessage(AddContentToHashDbAction.class,
                                                                              "AddContentToHashDbAction.multipleSelectionName");

    /**
     * AddContentToHashDbAction is a singleton to support multi-selection of nodes, since 
     * org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action from a node
     * if every node in the nodes array returns a reference to the same action object from 
     * Node.getActions(boolean).
     */
    public static synchronized AddContentToHashDbAction getInstance() {
        if (null == instance) {
            instance = new AddContentToHashDbAction();
        }        
        return instance;
    }

    private AddContentToHashDbAction() {
    }
    
    @Override
    public JMenuItem getPopupPresenter() {            
        return new AddContentToHashDbMenu();
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
    }  
    
    // Instances of this class are used to implement the a pop up menu for this
    // action.
    private final class AddContentToHashDbMenu extends JMenu { 

        AddContentToHashDbMenu() {
            super(SINGLE_SELECTION_NAME);
                        
            // Disable the menu if file ingest is in progress.
            IngestConfigurator ingestConfigurator = Lookup.getDefault().lookup(IngestConfigurator.class);
            if (null != ingestConfigurator && ingestConfigurator.isIngestRunning()) {
                setEnabled(false);
                return;
            }
            
            // Get any AbstractFile objects from the lookup of the currently focused top component. 
            final Collection<? extends AbstractFile> selectedFiles = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class);
            if (selectedFiles.isEmpty()) {
                setEnabled(false);
                return;
            }
            else if (selectedFiles.size() > 1) {
                setText(MULTIPLE_SELECTION_NAME);
            }
                        
            // Disable the menu if hashes have not been calculated.
            for (AbstractFile file : selectedFiles) {
                if (null == file.getMd5Hash()) {
                    setEnabled(false);
                    return;
                }
            }
                                    
            // Get the current set of updateable hash databases and add each
            // one to the menu as a separate menu item. Selecting a hash database
            // adds the selected files to the selected database.
            final List<HashDb> hashDatabases = HashDbManager.getInstance().getUpdateableHashSets();
            if (!hashDatabases.isEmpty()) {
                for (final HashDb database : hashDatabases) {
                    JMenuItem databaseItem = add(database.getHashSetName());
                    databaseItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            addFilesToHashSet(selectedFiles, database);
                        }
                    });
                }
            }
            else {
                JMenuItem empty = new JMenuItem(
                        NbBundle.getMessage(this.getClass(),
                                            "AddContentToHashDbAction.ContentMenu.noHashDbsConfigd"));
                empty.setEnabled(false);
                add(empty);                
            }
                        
            // Add a "New Hash Set..." menu item. Selecting this item invokes a
            // a hash database creation dialog and adds the selected files to the 
            // the new database.
            addSeparator();
            JMenuItem newHashSetItem = new JMenuItem(NbBundle.getMessage(this.getClass(),
                                                                         "AddContentToHashDbAction.ContentMenu.createDbItem"));
            newHashSetItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    HashDb hashDb = new HashDbCreateDatabaseDialog().getHashDatabase();
                    if (null != hashDb) {
                        HashDbManager.getInstance().save();
                        addFilesToHashSet(selectedFiles, hashDb);
                    }                    
                }
            });
            add(newHashSetItem);        
        }
        
        private void addFilesToHashSet(final Collection<? extends AbstractFile> files, HashDb hashSet) {
            for (AbstractFile file : files) {
                String md5Hash = file.getMd5Hash();
                if (null != md5Hash) {
                    try {
                        hashSet.addHashes(file);
                    }
                    catch (TskCoreException ex) {
                        //noinspection HardCodedStringLiteral
                        Logger.getLogger(AddContentToHashDbAction.class.getName()).log(Level.SEVERE, "Error adding to hash database", ex);
                        JOptionPane.showMessageDialog(null,
                                                      NbBundle.getMessage(this.getClass(),
                                                                          "AddContentToHashDbAction.addFilesToHashSet.unableToAddFileMsg",
                                                                          file.getName()),
                                                      NbBundle.getMessage(this.getClass(),
                                                                          "AddContentToHashDbAction.addFilesToHashSet.addToHashDbErr"),
                                                      JOptionPane.ERROR_MESSAGE);
                    }                    
                }  
                else {
                    JOptionPane.showMessageDialog(null,
                                                  NbBundle.getMessage(this.getClass(),
                                                                      "AddContentToHashDbAction.addFilesToHashSet.unableToAddFileSzMsg",
                                                                      files.size() > 1 ? "files" : "file"),
                                                  NbBundle.getMessage(this.getClass(),
                                                                      "AddContentToHashDbAction.addFilesToHashSet.addToHashDbErr"),
                                                  JOptionPane.ERROR_MESSAGE);
                    break;
                }
            }            
        }
    }    
}

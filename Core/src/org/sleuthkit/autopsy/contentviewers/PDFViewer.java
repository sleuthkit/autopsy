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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.icepdf.core.SecurityCallback;

import org.openide.util.NbBundle;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;

import org.icepdf.ri.common.ComponentKeyBinding;
import org.icepdf.ri.common.MyGUISecurityCallback;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.common.views.DocumentViewModelImpl;
import org.icepdf.ri.util.PropertiesManager;

/**
 * Application content viewer for PDF files.
 */
final class PDFViewer implements FileTypeViewer {

    private static final Logger logger = Logger.getLogger(PDFViewer.class.getName());

    private JPanel container;
    private final PropertiesManager propsManager;
    private final ResourceBundle messagesBundle;

    PDFViewer() {
        container = createNewContainer();
        messagesBundle = getMessagesBundle();
        propsManager = getCustomProperties();
    }

    @Override
    public List<String> getSupportedMIMETypes() {
        return Arrays.asList("application/pdf");
    }

    @Override
    public void setFile(AbstractFile file) {
        // The 'C' in IcePDFs MVC set up.
        SwingController controller = new SwingController(messagesBundle);

        // Builder for the 'V' in IcePDFs MVC set up
        SwingViewBuilder viewBuilder = new SwingViewBuilder(controller, propsManager);

        // The 'V' in IcePDFs MVC set up.
        JPanel icePdfPanel = viewBuilder.buildViewerPanel();

        // This connects keyboard commands performed on the view to the controller.
        // The only keyboard commands that the controller supports is Ctrl-C for 
        // copying selected text.
        ComponentKeyBinding.install(controller, icePdfPanel);

        // Ensure the preferredSize is in sync with the parent container.
        icePdfPanel.setPreferredSize(this.container.getPreferredSize());

        // Add the IcePDF view to the center of our container.
        this.container.add(icePdfPanel, BorderLayout.CENTER);
        
        // Disable all components until the document is ready to view.
        enableComponents(container, false);

        // Document is the 'M' in IcePDFs MVC set up. Read the data needed to 
        // populate the model in the background.
        new SwingWorker<Document, Void>() {
            @Override
            protected Document doInBackground() throws PDFException, PDFSecurityException, IOException {
                ReadContentInputStream stream = new ReadContentInputStream(file);
                Document doc = new Document();

                // Prompts the user for a password if the document is password
                // protected.
                doc.setSecurityCallback(createPasswordDialogCallback());

                // This will read the stream into memory and invoke the
                // security callback if needed.
                doc.setInputStream(stream, null);
                return doc;
            }

            @Override
            protected void done() {
                // Customize the view selection modes on the EDT. Each of these 
                // will cause UI widgets to be updated.
                try {
                    Document doc = get();
                    controller.openDocument(doc, file.getName());
                    // This makes the PDF viewer appear as one continuous 
                    // document, which is the default for most popular PDF viewers.
                    controller.setPageViewMode(DocumentViewControllerImpl.ONE_COLUMN_VIEW, true);
                    // This makes it possible to select text by left clicking and dragging.
                    controller.setDisplayTool(DocumentViewModelImpl.DISPLAY_TOOL_TEXT_SELECTION);
                    enableComponents(container, true);
                } catch (InterruptedException ex) {
                    // Do nothing.
                } catch (ExecutionException ex) {
                    Throwable exCause = ex.getCause();
                    if (exCause instanceof PDFSecurityException) {
                        showEncryptionDialog();
                    } else {
                        logger.log(Level.WARNING, String.format("PDF content viewer "
                                + "was unable to open document with id %d and name %s",
                                file.getId(), file.getName()), ex);
                        showErrorDialog();
                    }
                } catch (Throwable ex) {
                    logger.log(Level.WARNING, String.format("PDF content viewer "
                            + "was unable to open document with id %d and name %s",
                            file.getId(), file.getName()), ex);
                }
            }
        }.execute();
    }
    
    /**
     * Recursively enable/disable all components in this content viewer.
     * This will disable/enable all internal IcePDF Swing components too.
     */
    private void enableComponents(Container container, boolean enabled) {
        Component[] components = container.getComponents();
        for(Component component : components) {
            component.setEnabled(enabled);
            if (component instanceof Container) {
                enableComponents((Container)component, enabled);
            }
        }
    }

    @Override
    public Component getComponent() {
        return container;
    }

    @Override
    public void resetComponent() {
        container = createNewContainer();
    }

    // The container should have a BorderLayout otherwise the IcePDF panel may
    // not be visible.
    private JPanel createNewContainer() {
        return new JPanel(new BorderLayout());
    }

    @Override
    public boolean isSupported(AbstractFile file) {
        return getSupportedMIMETypes().contains(file.getMIMEType());
    }

    /**
     * Sets property values that will control how the view will be constructed
     * in IcePDFs MVC set up.
     */
    private PropertiesManager getCustomProperties() {
        Properties props = new Properties();

        // See link for available properties. https://www.icesoft.org/wiki/display/PDF/Customizing+the+Viewer
        props.setProperty(PropertiesManager.PROPERTY_SHOW_UTILITY_SAVE, "false");
        props.setProperty(PropertiesManager.PROPERTY_SHOW_UTILITY_OPEN, "false");
        props.setProperty(PropertiesManager.PROPERTY_SHOW_UTILITY_PRINT, "false");
        props.setProperty(PropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION, "false");
        props.setProperty(PropertiesManager.PROPERTY_SHOW_UTILITYPANE_ANNOTATION, "false");

        // This suppresses a pop-up, from IcePDF, that asks if you'd like to
        // save configuration changes to disk.
        props.setProperty("application.showLocalStorageDialogs", "false");
        
        return new PropertiesManager(System.getProperties(), props, messagesBundle);
    }
    
    private ResourceBundle getMessagesBundle() {
        return NbBundle.getBundle(PDFViewer.class);
    }

    @NbBundle.Messages({
        "PDFViewer.errorDialog=An error occurred while opening this PDF document. "
        + "Check the logs for more information. You may continue to use "
        + "this feature on other PDF documents."
    })
    private void showErrorDialog() {
        MessageNotifyUtil.Message.error(Bundle.PDFViewer_errorDialog());
    }

    @NbBundle.Messages({
        "PDFViewer.encryptedDialog=This document is password protected."
    })
    private void showEncryptionDialog() {
        MessageNotifyUtil.Message.error(Bundle.PDFViewer_encryptedDialog());
    }

    /**
     * Creates a callback that will prompt the user for password input.
     */
    private SecurityCallback createPasswordDialogCallback() {
        // MyGUISecurityCallback is a reference implementation from IcePDF.
        return new MyGUISecurityCallback(null, messagesBundle) {
            private String password;

            @Override
            public String requestPassword(Document document) {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        // Show the password dialog on the EDT.
                        this.password = super.requestPassword(document);
                    });
                    return this.password;
                } catch (InterruptedException | InvocationTargetException ex) {
                    return null;
                }
            }
        };
    }
}

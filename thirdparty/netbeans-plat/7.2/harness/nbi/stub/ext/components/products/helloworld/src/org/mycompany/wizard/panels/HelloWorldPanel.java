/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2011 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

package org.mycompany.wizard.panels;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import org.netbeans.installer.utils.ResourceUtils;
import org.netbeans.installer.utils.StringUtils;
import org.netbeans.installer.utils.SystemUtils;
import org.netbeans.installer.utils.helper.swing.NbiCheckBox;
import org.netbeans.installer.wizard.components.panels.DestinationPanel;
import org.netbeans.installer.wizard.containers.SwingContainer;
import org.netbeans.installer.wizard.ui.SwingUi;
import org.netbeans.installer.wizard.ui.WizardUi;

/**
 *
 * @author Dmitry Lipin
 */
public class HelloWorldPanel extends DestinationPanel {

    public HelloWorldPanel() {
        setProperty(TITLE_PROPERTY,
                DEFAULT_TITLE);
        setProperty(DESCRIPTION_PROPERTY,
                DEFAULT_DESCRIPTION);

        setProperty(DESTINATION_LABEL_TEXT_PROPERTY,
                DEFAULT_DESTINATION_LABEL_TEXT);
        setProperty(DESTINATION_BUTTON_TEXT_PROPERTY,
                DEFAULT_DESTINATION_BUTTON_TEXT);
    }

    @Override
    public WizardUi getWizardUi() {
        if (wizardUi == null) {
            wizardUi = new HelloWorldPanelUi(this);
        }

        return wizardUi;
    }

    @Override
    public void initialize() {
        super.initialize();
        if(getWizard().getProperty(CREATE_DESKTOP_SHORTCUT_PROPERTY) == null) {
            getWizard().setProperty(CREATE_DESKTOP_SHORTCUT_PROPERTY, "" + true);
        }
        if(getWizard().getProperty(CREATE_START_MENU_SHORTCUT_PROPERTY) == null) {
            getWizard().setProperty(CREATE_START_MENU_SHORTCUT_PROPERTY, "" + true);
        }
    }


    public static class HelloWorldPanelUi extends DestinationPanelUi {

        protected HelloWorldPanel panel;

        public HelloWorldPanelUi(HelloWorldPanel panel) {
            super(panel);


            this.panel = panel;
        }

        public SwingUi getSwingUi(SwingContainer container) {
            if (swingUi == null) {
                swingUi = new HelloWorldPanelSwingUi(panel, container);
            }

            return super.getSwingUi(container);
        }
    }

    public static class HelloWorldPanelSwingUi extends DestinationPanelSwingUi {

        protected HelloWorldPanel panel;
        private NbiCheckBox desktopShortcutComboBox;
        private NbiCheckBox startMenuShortcutComboBox;

        public HelloWorldPanelSwingUi(
                final HelloWorldPanel panel,
                final SwingContainer container) {
            super(panel, container);

            this.panel = panel;

            initComponents();
        }

        // protected ////////////////////////////////////////////////////////////////
        @Override
        protected void initialize() {
            desktopShortcutComboBox.setText(CREATE_DESKTOP_SHORTCUT_NAME);            
            desktopShortcutComboBox.setSelected(false);
            if(Boolean.parseBoolean(panel.getWizard().getProperty(CREATE_DESKTOP_SHORTCUT_PROPERTY))) {
                desktopShortcutComboBox.doClick();
            }

            startMenuShortcutComboBox.setText(
                    SystemUtils.isWindows() ? CREATE_START_MENU_SHORTCUT_NAME_WINDOWS :
                        (SystemUtils.isMacOS() ? CREATE_START_MENU_SHORTCUT_NAME_MAC :
                            CREATE_START_MENU_SHORTCUT_NAME_UNIX));
            startMenuShortcutComboBox.setSelected(false);
            if(Boolean.parseBoolean(panel.getWizard().getProperty(CREATE_START_MENU_SHORTCUT_PROPERTY))) {
                startMenuShortcutComboBox.doClick();
            }

            super.initialize();
        }

        @Override
        protected void saveInput() {
            super.saveInput();
            panel.getWizard().setProperty(
                    CREATE_DESKTOP_SHORTCUT_PROPERTY,
                    StringUtils.EMPTY_STRING + desktopShortcutComboBox.isSelected());
            
            panel.getWizard().setProperty(
                    CREATE_START_MENU_SHORTCUT_PROPERTY,
                    StringUtils.EMPTY_STRING + startMenuShortcutComboBox.isSelected());
        }

        @Override
        protected String validateInput() {
            String errorMessage = super.validateInput();
            return errorMessage;
        }

        // private //////////////////////////////////////////////////////////////////
        private void initComponents() {
            // selectedLocationField ////////////////////////////////////////////////
            desktopShortcutComboBox = new NbiCheckBox();
            startMenuShortcutComboBox = new NbiCheckBox();

            // this /////////////////////////////////////////////////////////////////
            add(desktopShortcutComboBox, new GridBagConstraints(
                    0, 2, // x, y
                    2, 1, // width, height
                    1.0, 0.0, // weight-x, weight-y
                    GridBagConstraints.LINE_START, // anchor
                    GridBagConstraints.HORIZONTAL, // fill
                    new Insets(15, 11, 0, 11), // padding
                    0, 0));                           // padx, pady - ???
            add(startMenuShortcutComboBox, new GridBagConstraints(
                    0, 3, // x, y
                    2, 1, // width, height
                    1.0, 0.0, // weight-x, weight-y
                    GridBagConstraints.LINE_START, // anchor
                    GridBagConstraints.HORIZONTAL, // fill
                    new Insets(7, 11, 0, 11), // padding
                    0, 0));                           // padx, pady - ???

        }
    }
    /////////////////////////////////////////////////////////////////////////////////
    // Constants
    public static final String DEFAULT_TITLE =
            ResourceUtils.getString(HelloWorldPanel.class,
            "P.title"); // NOI18N
    public static final String DEFAULT_DESCRIPTION =
            ResourceUtils.getString(HelloWorldPanel.class,
            "P.description"); // NOI18N
    public static final String DEFAULT_DESTINATION_LABEL_TEXT =
            ResourceUtils.getString(HelloWorldPanel.class,
            "P.destination.label.text"); // NOI18N
    public static final String DEFAULT_DESTINATION_BUTTON_TEXT =
            ResourceUtils.getString(HelloWorldPanel.class,
            "P.destination.button.text"); // NOI18N
    public static final String CREATE_DESKTOP_SHORTCUT_NAME =
            ResourceUtils.getString(HelloWorldPanel.class,
            "P.create.desktop.shortcut"); // NOI18N
    public static final String CREATE_START_MENU_SHORTCUT_NAME_WINDOWS =
            ResourceUtils.getString(HelloWorldPanel.class,
            "P.create.start.menu.shortcut.windows"); // NOI18N
    public static final String CREATE_START_MENU_SHORTCUT_NAME_UNIX =
            ResourceUtils.getString(HelloWorldPanel.class,
            "P.create.start.menu.shortcut.unix"); // NOI18N
    public static final String CREATE_START_MENU_SHORTCUT_NAME_MAC =
            ResourceUtils.getString(HelloWorldPanel.class,
            "P.create.start.menu.shortcut.macosx"); // NOI18N
    public static final String CREATE_DESKTOP_SHORTCUT_PROPERTY =
            "create.desktop.shortcut";
    public static final String CREATE_START_MENU_SHORTCUT_PROPERTY =
            "create.start.menu.shortcut";
}

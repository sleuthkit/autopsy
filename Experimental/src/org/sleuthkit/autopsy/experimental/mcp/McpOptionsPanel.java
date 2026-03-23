/*
 * Autopsy Forensic Browser
 *
 * Copyright 2026 Sleuth Kit Labs
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
package org.sleuthkit.autopsy.experimental.mcp;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Options panel for the Autopsy MCP server. Registered via
 * McpOptionsPanelController with the NetBeans Options dialog (Tools → Options).
 */
@Messages({
    "McpOptionsPanel.descriptionLabel.text=<html>The Autopsy MCP (Model Context Protocol) server allows AI assistants "
        + "such as Claude to query the currently open case using natural language. "
        + "When enabled, a local HTTP server starts on 127.0.0.1 when a case is opened, "
        + "and an auth token is written to ~/.autopsy/mcp-token for use by the STDIO wrapper.</html>",
    "McpOptionsPanel.enabledCheckBox.text=Enable MCP server",
    "McpOptionsPanel.windowsOnlyLabel.text=MCP server is only supported on Windows.",
    "McpOptionsPanel.stdioLocationLabel.text=STDIO wrapper location:",
    "McpOptionsPanel.stdioNotFoundLabel.text=Not found",
    "McpOptionsPanel.restartNoteLabel.text=Changes take effect after restarting Autopsy.",
    "McpOptionsPanel.restartDialogTitle.text=Restart Required",
    "McpOptionsPanel.restartDialogMessage.text=Autopsy must be restarted for MCP server changes to take effect."
})
public class McpOptionsPanel extends JPanel {

    static final String PREF_MCP_ENABLED = "MCP_SERVER_ENABLED"; //NON-NLS

    private final McpOptionsPanelController controller;
    private final JCheckBox enabledCheckBox;
    private final JTextField stdioPathField;

    McpOptionsPanel(McpOptionsPanelController controller) {
        this.controller = controller;
        enabledCheckBox = new JCheckBox(Bundle.McpOptionsPanel_enabledCheckBox_text());
        stdioPathField  = new JTextField();
        initLayout();
    }

    private void initLayout() {
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.gridx  = 0;
        gbc.gridy  = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;

        // Description
        JLabel descriptionLabel = new JLabel(Bundle.McpOptionsPanel_descriptionLabel_text());
        add(descriptionLabel, gbc);

        // Checkbox (disabled on non-Windows)
        gbc.gridy++;
        gbc.gridwidth = 2;
        if (PlatformUtil.isWindowsOS()) {
            enabledCheckBox.addActionListener(e -> {
                controller.changed();
            });
            add(enabledCheckBox, gbc);

            gbc.gridy++;
            add(new JLabel(Bundle.McpOptionsPanel_restartNoteLabel_text()), gbc);
        } else {
            add(new JLabel(Bundle.McpOptionsPanel_windowsOnlyLabel_text()), gbc);
        }

        // STDIO wrapper location label
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.weightx   = 0.0;
        add(new JLabel(Bundle.McpOptionsPanel_stdioLocationLabel_text()), gbc);

        // STDIO path field (read-only)
        gbc.gridx   = 1;
        gbc.weightx = 1.0;
        stdioPathField.setEditable(false);
        stdioPathField.setColumns(40);
        add(stdioPathField, gbc);

        // Fill remaining vertical space
        gbc.gridx     = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.weighty   = 1.0;
        gbc.fill      = GridBagConstraints.BOTH;
        add(new JPanel(), gbc);
    }

    /**
     * Returns true if the MCP server is enabled in preferences.
     */
    static boolean isMcpEnabled() {
        return NbPreferences.forModule(McpOptionsPanel.class).getBoolean(PREF_MCP_ENABLED, false);
    }

    /**
     * Loads current preferences into the UI. Called when the Options dialog opens.
     */
    void load() {
        enabledCheckBox.setSelected(isMcpEnabled());
        stdioPathField.setText(findStdioExePath());
    }

    /**
     * Saves current UI state to preferences. Called when OK or Apply is clicked.
     * Shows a restart-required dialog if the enabled state changed.
     */
    void store() {
        boolean wasEnabled = isMcpEnabled();
        boolean nowEnabled = enabledCheckBox.isSelected();
        NbPreferences.forModule(McpOptionsPanel.class)
                .putBoolean(PREF_MCP_ENABLED, nowEnabled);
        if (wasEnabled != nowEnabled) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    this,
                    Bundle.McpOptionsPanel_restartDialogMessage_text(),
                    Bundle.McpOptionsPanel_restartDialogTitle_text(),
                    JOptionPane.WARNING_MESSAGE));
        }
    }

    /**
     * Locates autopsy-mcp-stdio.exe in the Autopsy installation bin directory.
     */
    private static String findStdioExePath() {
        String exePath = PlatformUtil.getInstallPath()
                + File.separator + "bin"          //NON-NLS
                + File.separator + "autopsy-mcp-stdio.exe"; //NON-NLS
        File exeFile = new File(exePath);
        return exeFile.exists() ? exeFile.getAbsolutePath()
                                : Bundle.McpOptionsPanel_stdioNotFoundLabel_text();
    }
}

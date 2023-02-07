/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.contentviewers.binary;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;

/**
 * Drop down button.
 */
@ParametersAreNonnullByDefault
public class DropDownButton extends JButton {

    private final DropDownButtonPanel buttonPanel;
    private final JPopupMenu popupMenu;

    public DropDownButton(Action action, JPopupMenu popupMenu) {
        this.popupMenu = popupMenu;
        buttonPanel = new DropDownButtonPanel();

        init(action);
    }

    private void init(Action action) {
        setFocusable(false);
        setActionText((String) action.getValue(Action.NAME));
        addActionListener(action);
        action.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            setEnabled(action.isEnabled());
        });
        String toolTipText = (String) action.getValue(Action.SHORT_DESCRIPTION);
        setToolTipText(toolTipText);
        setActionTooltip(toolTipText);

        setMargin(new Insets(0, 0, 0, 0));
        add(buttonPanel);
        JLabel actionButton = buttonPanel.getActionButton();
        JButton menuButton = buttonPanel.getMenuButton();

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent me) {
            }

            @Override
            public void mousePressed(MouseEvent me) {
                if (me.getSource() == actionButton) {
                    menuButton.setSelected(true);
                }
            }

            @Override
            public void mouseReleased(MouseEvent me) {
                if (me.getSource() == actionButton) {
                    menuButton.setSelected(false);
                }
            }

            @Override
            public void mouseEntered(MouseEvent me) {
                setRolloverBorder();
            }

            @Override
            public void mouseExited(MouseEvent me) {
                unsetRolloverBorder();
            }
        };

        actionButton.addMouseListener(ma);
        menuButton.addMouseListener(ma);

        menuButton.addActionListener((ActionEvent ae) -> {
            popupMenu.show(actionButton, 0, actionButton.getSize().height);
        });
    }

    protected void setRolloverBorder() {
        JButton menuButton = buttonPanel.getMenuButton();
        menuButton.setBorderPainted(true);
    }

    protected void unsetRolloverBorder() {
        JButton menuButton = buttonPanel.getMenuButton();
        menuButton.setBorderPainted(false);
    }

    public void setActionText(String value) {
        buttonPanel.getActionButton().setText(" " + value + " ");
    }

    public void setActionTooltip(String text) {
        buttonPanel.getActionButton().setToolTipText(text);
        buttonPanel.getMenuButton().setToolTipText(text);
    }
}

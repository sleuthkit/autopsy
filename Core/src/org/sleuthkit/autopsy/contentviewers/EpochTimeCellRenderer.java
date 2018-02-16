/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.contentviewers;

import java.awt.Component;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 *
 * @author raman
 */
public class EpochTimeCellRenderer extends DefaultTableCellRenderer {
    
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(FileViewer.class.getName());

    private static final String FORMAT_STRING = "yyyy/MM/dd HH:mm:ss"; //NON-NLS
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat(FORMAT_STRING);

    private final boolean renderAsEpoch;
    
    public EpochTimeCellRenderer(boolean renderAsEpoch) {
        this.renderAsEpoch = renderAsEpoch;
    }
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value != null) {
            
            String textStr = "No Value";
            try {
                if (value instanceof Node.Property<?>) {
                    Node.Property<?> np = (Node.Property)value;
                    textStr = np.getValue().toString();
                }
                if (renderAsEpoch) {
                    long epochTime = Long.parseUnsignedLong(textStr);
                    if (epochTime > 0 ) {
 
                        Font f = getFont();
                        setFont(f.deriveFont(f.getStyle() | Font.ITALIC));
                        setText(dateFormat.format(new Date(epochTime)));
                    }
                    else {
                         setText(textStr);
                    }
                }
                else {
                    setText(textStr);
                }
                
            }
            catch (NumberFormatException e) {
                setText(textStr); 
                LOGGER.log(Level.INFO, "Error converting column value to number.", e); //NON-NLS
            } catch (IllegalAccessException | InvocationTargetException ex) {
                setText("");
                LOGGER.log(Level.SEVERE, "Error in getting column value.", ex); //NON-NLS
            }
        }
        else {
            setText("");
        }
       
        return this;
    }
    
    
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.openide.util.Lookup;

/**
 *
 * @author dfickling
 */
public class FileSearchAction extends AbstractAction{
    
    public FileSearchAction(String title) {
        super(title);
    }
    @Override
    public void actionPerformed(ActionEvent e) {
        FileSearchProvider searcher = Lookup.getDefault().lookup(FileSearchProvider.class);
        searcher.showDialog();
    }
    
}

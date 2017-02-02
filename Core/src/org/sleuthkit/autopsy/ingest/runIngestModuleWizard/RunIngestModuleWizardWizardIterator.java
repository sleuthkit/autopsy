/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.ingest.runIngestModuleWizard;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.sleuthkit.autopsy.ingest.IngestProfileMap;

public final class RunIngestModuleWizardWizardIterator implements WizardDescriptor.Iterator<WizardDescriptor> {

    private final static String DEFAULT_CONTEXT = "org.sleuthkit.autopsy.ingest.runIngestModuleAction";

    private int index;

    private List<ShortCircuitableWizardPanel> panels;

    /**
     * @return the DEFAULT_CONTEXT
     */
    public static String getDefaultContext() {
        return DEFAULT_CONTEXT;
    }

    private List<ShortCircuitableWizardPanel> getPanels() {
        if (panels == null) {
            panels = new ArrayList<>();
            TreeMap<String, IngestProfileMap.IngestProfile> profileMap = new IngestProfileMap().getIngestProfileMap();
            if (!profileMap.isEmpty()) {
                panels.add(new RunIngestModuleWizardWizardPanel1());
            }

            panels.add(new RunIngestModuleWizardWizardPanel2());
            String[] steps = new String[panels.size()];
            for (int i = 0; i < panels.size(); i++) {
                Component c = panels.get(i).getComponent();
                // Default step name to component name of panel.
                steps[i] = c.getName();
                if (c instanceof JComponent) { // assume Swing components
                    JComponent jc = (JComponent) c;
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, i);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, steps);
                    jc.putClientProperty(WizardDescriptor.PROP_AUTO_WIZARD_STYLE, true);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DISPLAYED, true);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_NUMBERED, true);
                }
            }
        }
        return panels;
    }

    @Override
    public ShortCircuitableWizardPanel current() {
        return getPanels().get(index);
    }

    @Override
    public String name() {
        return index + 1 + ". from " + getPanels().size();
    }

    @Override
    public boolean hasNext() {
        return (index < getPanels().size() - 1) && current().shouldCheckForNext();
    }

    @Override
    public boolean hasPrevious() {
        return index > 0;
    }

    @Override
    public void nextPanel() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        index++;
    }

    @Override
    public void previousPanel() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        index--;
    }

    // If nothing unusual changes in the middle of the wizard, simply:
    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }
    // If something changes dynamically (besides moving between panels), e.g.
    // the number of panels changes in response to user input, then use
    // ChangeSupport to implement add/removeChangeListener and call fireChange
    // when needed

}

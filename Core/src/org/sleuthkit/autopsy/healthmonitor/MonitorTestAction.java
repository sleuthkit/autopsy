/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.healthmonitor;

import java.awt.Frame;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;

/*
// TODO: debug
    synchronized void printCurrentState() {
        System.out.println("\nTiming Info Map:");
        for(String name:timingInfoMap.keySet()) {
            System.out.print(name + "\t");
            timingInfoMap.get(name).print();
        }
    }

private void deleteDatabase() {
        try {
            // Use the same database settings as the case
            CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
            Class.forName("org.postgresql.Driver"); //NON-NLS
            try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/postgres", db.getUserName(), db.getPassword()); //NON-NLS
                    Statement statement = connection.createStatement();) {
                String deleteCommand = "DROP DATABASE \"" + DATABASE_NAME + "\""; //NON-NLS
                statement.execute(deleteCommand);
            }
        } catch (UserPreferencesException | ClassNotFoundException | SQLException ex) {
            logger.log(Level.SEVERE, "Failed to delete health monitor database", ex);
        }
    }
*/


@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.healthmonitor.MonitorTestAction")
@ActionReference(path = "Menu/Tools", position = 7014)
@ActionRegistration(displayName = "#CTL_MonitorTestAction", lazy = false)
@NbBundle.Messages({"CTL_MonitorTestAction=Test health monitor"})
public final class MonitorTestAction extends CallableSystemAction {

    private static final String DISPLAY_NAME = "Test health monitor";

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void performAction() {

        Frame mainWindow = WindowManager.getDefault().getMainWindow();
        
        TestPanel panel = new TestPanel(mainWindow, false);
        panel.setVisible(true);
 
    }
    
    @Override
    public String getName() {
        return DISPLAY_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }
}

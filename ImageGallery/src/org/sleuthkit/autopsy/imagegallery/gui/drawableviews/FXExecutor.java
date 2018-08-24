package org.sleuthkit.autopsy.imagegallery.gui.drawableviews;

import java.util.concurrent.Executor;
import javafx.application.Platform;

/**
 *
 * An executor that queues all tasks to be run on the JavaFX thread.
 */
public class FXExecutor implements Executor {

    @Override
    public void execute(Runnable command) {
        Platform.runLater(command);
    }

}

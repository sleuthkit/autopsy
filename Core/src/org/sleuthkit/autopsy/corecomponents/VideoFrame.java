package org.sleuthkit.autopsy.corecomponents;

import java.awt.Image;

/**
 *
 */
public class VideoFrame {

    private Image frame;
    private long timeMillis;

    public VideoFrame(Image frame, long timeMillis) {
        this.frame = frame;
        this.timeMillis = timeMillis;
    }

    public Image getFrame() {
        return frame;
    }

    public long getTimeMillis() {
        return timeMillis;
    }
}

/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.elements.AppSink;

/**
 * This is a JavaFX Video renderer for GStreamer
 */
final class JavaFxAppSink extends AppSink {

    private static final String CAP_MIME_TYPE = "video/x-raw";
    private static final String CAP_BYTE_ORDER = (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? "format=BGRx" : "format=xRGB");
    private static final int PROP_MAX_BUFFERS = 5000;
    
    private final JavaFxFrameUpdater updater;

    /**
     * Creates a new AppSink that hooks an ImageView into a JFXPanel. This AppSink
     * comes prepackaged with an AppSink listener to accomplish the previous statement.
     * 
     * @param name AppSink internal name
     * @param target JFXPanel to display video playback in
     */
    public JavaFxAppSink(String name, JFXPanel target) {
        super(name);
        set("emit-signals", true);
        updater = new JavaFxFrameUpdater(target);
        connect((AppSink.NEW_SAMPLE) updater);
        connect((AppSink.NEW_PREROLL) updater);
        setCaps(new Caps(
                String.format("%s, %s", CAP_MIME_TYPE, CAP_BYTE_ORDER)));
        set("max-buffers", PROP_MAX_BUFFERS);
        set("drop", true);
    }

    /**
     * Clear the current frame in the JFXPanel
     */
    public void clear() {
        disconnect((AppSink.NEW_SAMPLE) updater);
        disconnect((AppSink.NEW_PREROLL) updater);
        updater.clear();
    }
    
    /**
     * Responsible for keeping the ImageView that is hooked into the JFXPanel up-to-date
     * with the most current or available frame from GStreamer.
     */
    static class JavaFxFrameUpdater implements AppSink.NEW_SAMPLE, AppSink.NEW_PREROLL {
        private final ImageView fxImageView;

        public JavaFxFrameUpdater(JFXPanel target) {
            //We should probably pass an ImageView instead of a JFXPanel to make
            //it more reuseable
            fxImageView = new ImageView();  // Will hold the current video frame.
            BorderPane borderpane = new BorderPane(fxImageView); // Center and size ImageView.
            Scene scene = new Scene(borderpane); // Root of the JavaFX tree.
            // Although the documentation for JFXPanel.setScene() claims that it
            // can be called on either the EDT or the JavaFX thread, with JavaFX 11
            // it doesn't work unless you call it on the JavaFX application thread.
            Platform.runLater(() -> {
                target.setScene(scene);        
            });

            // Bind size of image to that of scene, while keeping proportions
            fxImageView.fitWidthProperty().bind(scene.widthProperty());
            fxImageView.fitHeightProperty().bind(scene.heightProperty());
            fxImageView.setPreserveRatio(true);
            fxImageView.setSmooth(true);
            fxImageView.setCache(true);
        }

        /**
         * Updates the ImageView when a brand new frame is in the pipeline.
         * 
         * @param appSink Pipeline containing the new frame
         * @return Result of update
         */
        @Override
        public FlowReturn newSample(AppSink appSink) {
            return setSample(appSink.pullSample());
        }

        /**
         * Set the ImageView to the input sample. Sample here is synonymous with 
         * frame.
         * 
         * @param input Frame
         * @return Result of update
         */
        public FlowReturn setSample(Sample input) {
                Buffer buffer = input.getBuffer();
                ByteBuffer byteBuffer = buffer.map(false);
                if (byteBuffer != null) {
                    Structure capsStruct = input.getCaps().getStructure(0);
                    int width = capsStruct.getInteger("width");
                    int height = capsStruct.getInteger("height");
                    byte[] byteArray = new byte[width * height * 4];
                    byteBuffer.get(byteArray);
                    Image videoFrame = convertBytesToImage(byteArray, width, height);
                    fxImageView.setImage(videoFrame);
                    buffer.unmap();
                }
                input.dispose();

                //Keep frames rolling
                return FlowReturn.OK;
        }
        
        /**
         * Updates the ImageView with the next frame in the pipeline, without
         * removing it. This function is invoked when Gstreamer is not in a 
         * PLAYING state, but we can peek at what's to come.
         * 
         * It's essential for displaying the initial frame when a video is first 
         * selected.
         * 
         * @param sink Pipeline containing video data
         * @return 
         */
        @Override
        public FlowReturn newPreroll(AppSink sink) {
            //Grab the next frame without removing it from the pipeline
            Sample sample = sink.pullPreroll();
            return setSample(sample);
        }

        /**
         * Create an image from a byte array of pixels.
         *
         * @param pixels The byte array of pixels.
         * @param width The width of the image.
         * @param height The height of the image.
         *
         * @return The image.
         */
        private Image convertBytesToImage(byte[] pixels, int width, int height) {
            WritableImage image = new WritableImage(width, height);
            PixelWriter pixelWriter = image.getPixelWriter();
            pixelWriter.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), pixels, 0, width * 4);
            return image;
        }
        
        /**
         * Remove the current frame from the display
         */
        void clear() {
            fxImageView.setImage(null);
        }
    }
}

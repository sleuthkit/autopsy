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
 * This is a video renderer for GStreamer.
 */
final class GstVideoRendererPanel extends JFXPanel {
    
    private static final String CAP_MIME_TYPE = "video/x-raw";
    private static final String CAP_BYTE_ORDER = (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? "format=BGRx" : "format=xRGB");
    private static final int PROP_MAX_BUFFERS = 5000;
    private AppSink videoSink;
    private ImageView fxImageView;
    
    /**
     * Create an instance.
     */
    GstVideoRendererPanel() {
        initImageView();
        initVideoSink();
    }
    
    /**
     * Initialize the ImageView to show the current frame.
     */
    private void initImageView() {
        fxImageView = new ImageView();  // Will hold the current video frame.
        BorderPane borderpane = new BorderPane(fxImageView); // Center and size ImageView.
        Scene scene = new Scene(borderpane); // Root of the JavaFX tree.
        setScene(scene);

        // Bind size of image to that of scene, while keeping proportions
        fxImageView.fitWidthProperty().bind(scene.widthProperty());
        fxImageView.fitHeightProperty().bind(scene.heightProperty());
        fxImageView.setPreserveRatio(true);
        fxImageView.setSmooth(true);
        fxImageView.setCache(true);
    }
    
    /**
     * Initialize the video sink.
     */
    private void initVideoSink() {
        videoSink = new AppSink("GstVideoComponent");
        videoSink.set("emit-signals", true);
        AppSinkListener gstListener = new AppSinkListener();
        videoSink.connect(gstListener);
        videoSink.setCaps(new Caps(
                String.format("%s, %s", CAP_MIME_TYPE, CAP_BYTE_ORDER)));
        videoSink.set("max-buffers", PROP_MAX_BUFFERS);
        videoSink.set("drop", true);
    }
    
    /**
     * Get the video sink.
     * 
     * @return The video sink.
     */
    AppSink getVideoSink() {
        return videoSink;
    }
    
    /**
     * Listen for NEW_SAMPLE events to update the ImageView with the newest
     * video frame.
     */
    class AppSinkListener implements AppSink.NEW_SAMPLE {
        
        private Image videoFrame;
        private int lastWidth = 0;
        private int lastHeight = 0;
        private byte[] byteArray;
        
        @Override
        public FlowReturn newSample(AppSink appSink) {
            Sample sample = appSink.pullSample();
            Buffer buffer = sample.getBuffer();
            ByteBuffer byteBuffer = buffer.map(false);
            if (byteBuffer != null) {
                Structure capsStruct = sample.getCaps().getStructure(0);
                int width = capsStruct.getInteger("width");
                int height = capsStruct.getInteger("height");
                if (width != lastWidth || height != lastHeight) {
                    lastWidth = width;
                    lastHeight = height;
                    byteArray = new byte[width * height * 4];
                }
                byteBuffer.get(byteArray);
                videoFrame = convertBytesToImage(byteArray, width, height);
                Platform.runLater(() -> {
                    fxImageView.setImage(videoFrame);
                });
                buffer.unmap();
            }
            sample.dispose();
            
            return FlowReturn.OK;
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
    }
}

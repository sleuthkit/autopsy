/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corelibs;

import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;

/**
 * Scalr wrapper to deal with exports and provide thread-safety
 *
 */
public class ScalrWrapper {

    public static synchronized BufferedImage resize(BufferedImage input, int width, int height) {
        return Scalr.resize(input, width, height, Scalr.OP_ANTIALIAS);
    }

    public static synchronized BufferedImage resize(BufferedImage input, int size) {
        return Scalr.resize(input, size, Scalr.OP_ANTIALIAS);
    }

    public static synchronized BufferedImage resize(BufferedImage bufferedImage, Method method, Scalr.Mode mode, int width, int height, BufferedImageOp ...ops) {
        return Scalr.resize(bufferedImage, method, mode, width, height, ops);
    }
    
    public static synchronized BufferedImage resizeHighQuality(BufferedImage input, int width, int height) {
        return Scalr.resize(input, Method.QUALITY, width, height, Scalr.OP_ANTIALIAS);
    }

    public static synchronized BufferedImage resizeFast(BufferedImage input, int size) {
        return Scalr.resize(input, Method.SPEED, Scalr.Mode.AUTOMATIC, size, Scalr.OP_ANTIALIAS);
    }

    public static synchronized BufferedImage resizeFast(BufferedImage input, int width, int height) {
        return Scalr.resize(input, Method.SPEED, Scalr.Mode.AUTOMATIC, width, height, Scalr.OP_ANTIALIAS);
    }

    public static synchronized BufferedImage cropImage(BufferedImage input, int width, int height) {
        return Scalr.crop(input, width, height, (BufferedImageOp) null);
    }


}

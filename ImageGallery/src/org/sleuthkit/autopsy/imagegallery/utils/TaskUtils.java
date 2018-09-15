/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.utils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javafx.concurrent.Task;

/**
 *
 */
public final class TaskUtils {

    private TaskUtils() {
    }

    public static <T> Task<T> taskFrom(Callable<T> callable) {
        return new Task<T>() {
            @Override
            protected T call() throws Exception {
                return callable.call();
            }
        };
    }

   public  static ListeningExecutorService getExecutorForClass(Class<?> clazz) {
        return MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("Image Gallery " + clazz.getSimpleName() + " BG Thread").build()));
    }
}

/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.Notification;
import org.openide.awt.NotificationDisplayer;
import org.openide.util.ImageUtilities;

/**
 * Utility for displaying messages and notifications in status area. Wraps
 * around NB RCP NotificationDisplayer.
 *
 * Messages can optionally contain click-able Actions.
 *
 * Based on:
 * http://qbeukes.blogspot.com/2009/11/netbeans-platform-notifications.html
 *
 * @license Apache License 2.0
 */
public class MessageNotifyUtil {

    private MessageNotifyUtil() {
    }

    public enum MessageType {

        INFO(NotifyDescriptor.INFORMATION_MESSAGE, "info-icon-16.png"),
        ERROR(NotifyDescriptor.ERROR_MESSAGE, "error-icon-16.png"),
        WARNING(NotifyDescriptor.WARNING_MESSAGE, "warning-icon-16.png");
        private int notifyDescriptorType;
        private Icon icon;

        private MessageType(int notifyDescriptorType, String resourceName) {
            this.notifyDescriptorType = notifyDescriptorType;
            if (resourceName == null) {
                icon = new ImageIcon();
            } else {
                icon = loadIcon(resourceName);
            }
        }

        private static Icon loadIcon(String resourceName) {
            Icon icon = ImageUtilities.loadImageIcon("org/sleuthkit/autopsy/images/" + resourceName, false);
            if (icon == null) {
                Logger logger = Logger.getLogger(org.sleuthkit.autopsy.coreutils.MessageNotifyUtil.MessageType.class.getName());
                logger.log(Level.SEVERE, "Failed to load icon resource: " + resourceName + ". Using blank image.");
                icon = new ImageIcon();
            }
            return icon;
        }

        int getNotifyDescriptorType() {
            return notifyDescriptorType;
        }

        Icon getIcon() {
            return icon;
        }
    }

    /**
     * Utility to display messages
     */
    public static class Message {

        private Message() {
        }

        /**
         * @return The dialog displayer used to show message boxes
         */
        public static DialogDisplayer getDialogDisplayer() {
            return DialogDisplayer.getDefault();
        }

        /**
         * Show a message of the specified type
         *
         * @param message message to show
         * @param messageType message type to show
         */
        public static void show(String message, MessageType messageType) {
            getDialogDisplayer().notify(new NotifyDescriptor.Message(message,
                    messageType.getNotifyDescriptorType()));
        }

        /**
         * Show an information dialog
         *
         * @param message message to show
         */
        public static void info(String message) {
            show(message, MessageType.INFO);
        }

        /**
         * Show an error dialog
         *
         * @param message message to shpw
         */
        public static void error(String message) {
            show(message, MessageType.ERROR);
        }

        /**
         * Show an warning dialog
         *
         * @param message message to show
         */
        public static void warn(String message) {
            show(message, MessageType.WARNING);
        }
    }

    /**
     * Utility to display notifications with baloons
     */
    public static class Notify {

        //notifications to keep track of and to reset when case is closed
        private static final List<Notification> notifications = Collections.synchronizedList(new ArrayList<Notification>());

        private Notify() {
        }
        
        /**
         * Clear pending notifications
         * Should really only be used by Case
         */
        public static void clear() {
            for (Notification n : notifications) {
                n.clear();
            }
            notifications.clear();
        }

        /**
         * Show message with the specified type and action listener
         */
        public static void show(String title, String message, MessageType type, ActionListener actionListener) {
            Notification newNotification = 
                    NotificationDisplayer.getDefault().notify(title, type.getIcon(), message, actionListener);
            notifications.add(newNotification);
        }

        /**
         * Show message with the specified type and a default action which
         * displays the message using MessageNotifyUtil.Message with the same
         * message type
         *
         * @param title message title
         * @param message message text
         * @param type type of the message
         */
        public static void show(String title, final String message, final MessageType type) {
            ActionListener actionListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    MessageNotifyUtil.Message.show(message, type);
                }
            };

            show(title, message, type, actionListener);
        }

        /**
         * Show an information notification
         *
         * @param title message title
         * @param message message text
         */
        public static void info(String title, String message) {
            show(title, message, MessageType.INFO);
        }

        /**
         * Show an error notification
         *
         * @param title message title
         * @param message message text
         */
        public static void error(String title, String message) {
            show(title, message, MessageType.ERROR);
        }

        /**
         * Show an warning notification
         *
         * @param title message title
         * @param message message text
         */
        public static void warn(String title, String message) {
            show(title, message, MessageType.WARNING);
        }
    }
}

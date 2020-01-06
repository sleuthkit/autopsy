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
package org.sleuthkit.autopsy.thunderbirdparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Given a list of email messages arranges the message into threads using the
 * message reference lists.
 *
 * This threader is based heavely off of the algorithum found at
 * <a href="https://www.jwz.org/doc/threading.html">
 * "message threading." by Jamie Zawinski</a>
 *
 */
final class EmailMessageThreader {

    private int bogus_id_count = 0;
    
    private EmailMessageThreader(){}

    public static void threadMessages(List<EmailMessage> emailMessages) {
        EmailMessageThreader instance = new EmailMessageThreader();
        
        Map<String, EmailContainer> id_table = instance.createIDTable(emailMessages);
        Set<EmailContainer> rootSet = instance.getRootSet(id_table);

        instance.pruneEmptyContainers(rootSet);

        Set<EmailContainer> finalRootSet = instance.groupBySubject(rootSet);

        instance.assignThreadIDs(finalRootSet);
    }

    /**
     * Walks the list of emailMessages creating a Container object for each
     * unique message ID found. Adds the emailMessage to the container where
     * possible.
     *
     * @param emailMessages
     *
     * @return - HashMap of all message where the key is the message-ID of the message
     */
    private Map<String, EmailContainer> createIDTable(List<EmailMessage> emailMessages) {
        HashMap<String, EmailContainer> id_table = new HashMap<>();

        for (EmailMessage message : emailMessages) {
            String messageID = message.getMessageID();

            // Check the id_table for an existing Container for message-id
            EmailContainer container = id_table.get(messageID);

            // An existing container for message-id was found
            if (container != null) {
                // If the existing Container has a message already assocated with it
                // emailMessage maybe a duplicate, so we don't lose the existance of 
                // the duplicate message assign it a bogus message-id
                if (container.hasMessage()) {
                    messageID = String.format("<Bogus-id: %d >", bogus_id_count++);
                    container = null;
                } else {
                    container.setMessage(message);
                }
            }

            if (container == null) {
                container = new EmailContainer(message);
                id_table.put(messageID, container);
            }

            processMessageReferences(message, container, id_table);
        }

        return id_table;
    }

    /**
     * Loops throught message's list of references, creating objects as needed
     * and setting up the parent child relationships amoung the messages.
     *
     * @param message   The current email messags
     * @param container Container object for message
     * @param id_table  Hashtable of known message-id\container pairs
     */
    void processMessageReferences(EmailMessage message, EmailContainer container, Map<String, EmailContainer> id_table) {
        List<String> referenceList = message.getReferences();

        // Make sure the inReplyToID is in the list of references
        String inReplyToID = message.getInReplyToID();
        if (inReplyToID != null && !inReplyToID.isEmpty()) {
            if (referenceList == null) {
                referenceList = new ArrayList<>();
            }

            referenceList.add(inReplyToID);
        }

        // No references, nothing to do
        if (referenceList == null) {
            return;
        }

        EmailContainer parent_ref = null;
        EmailContainer ref;

        for (String refID : referenceList) {
            // Check id_table to see if there is already a container for this
            // reference id, if not create a new Container and add to table
            ref = id_table.get(refID);

            if (ref == null) {
                ref = new EmailContainer();
                id_table.put(refID, ref);
            }

            // Set the parent\child relationship between parent_ref and ref
            if (parent_ref != null
                    && !ref.hasParent()
                    && !parent_ref.equals(ref)
                    && !parent_ref.isChild(ref)) {
                ref.setParent(parent_ref);
                parent_ref.addChild(ref);
            }

            parent_ref = ref;
        }

        // If the parent_ref and container are already linked, don't change 
        // anything
        if (parent_ref != null
                && (parent_ref.equals(container)
                || container.isChild(parent_ref))) {
            parent_ref = null;
        }

        // If container already has a parent, the parent was assumed based on
        // the list of references from another message.  parent_ref will be
        // the real parent of container so throw away the old parent and set a
        // new one.
        if (container.hasParent()) {
            container.getParent().removeChild(container);
            container.setParent(null);
        }

        if (parent_ref != null) {
            container.setParent(container);
            parent_ref.addChild(container);
        }
    }

    /**
     * Creates a set of root container messages from the message-ID hashtable. A
     * root Container is container that does not have a parent container.
     *
     * @param id_table Table of all known Containers
     *
     * @return A set of the root containers.
     */
    Set<EmailContainer> getRootSet(Map<?, EmailContainer> id_table) {
        HashSet<EmailContainer> rootSet = new HashSet<>();

        id_table.values().stream().filter((container)
                -> (!container.hasParent())).forEachOrdered((container) -> {
            rootSet.add(container);
        });

        return rootSet;
    }

    /**
     * Remove Containers from containerSet if they do not have a message or
     * children.
     *
     * @param containerSet A set of Container objects
     */
    void pruneEmptyContainers(Set<EmailContainer> containerSet) {
        Set<EmailContainer> containersToRemove = new HashSet<>();
        containerSet.forEach((container) -> {
            if (!container.hasMessage() && !container.hasChildren()) {
                containersToRemove.add(container);
            } else {
                pruneChildren(container);
            }
        });

        containerSet.removeAll(containersToRemove);
    }

    /**
     * Recursively work through the list of parent's children removing empty
     * containers.  If the passed in container does not have a message
     * associated with it, it will get removed and its children will be assigned
     * to their grandparent.
     *
     * @param parent returns true if their where children pruned, otherwise false.
     */
    boolean pruneChildren(EmailContainer parent) {
        if (parent == null) {
            return false;
        }

        Set<EmailContainer> children = parent.getChildren();
        
        if (children == null) {
            return false;
        }

        EmailContainer grandParent = parent.getParent();
        Set<EmailContainer> remove = new HashSet<>();
        Set<EmailContainer> add = new HashSet<>();
        for (EmailContainer child : parent.getChildren()) {
            if (pruneChildren(child)) {
                remove.add(child);
                add.addAll(child.getChildren());
                child.setParent(null);
                child.clearChildren();

            }
        }

        parent.addChildren(add);
        parent.removeChildren(remove);

        if (!parent.hasMessage() && grandParent != null) {
            children.forEach((child) -> {
                child.setParent(grandParent);
            });
            return true;
        }

        return false;
    }

    /**
     * Now that the emails are grouped together by references\message ID take
     * another pass through and group together messages with the same simplified
     * subject.
     * 
     * The purpose of grouping by subject is to bring threads together that may 
     * been separated due to missing messages. Group by subject to put attempt
     * to put these threads together.
     *
     * This may cause "root" messages with identical subjects to get grouped
     * together as children of an empty container. The code that uses the thread
     * information can decide what to do in that situation as those message
     * maybe part of a common thread or maybe their own unique messages.
     *
     * @param rootSet
     *
     * @return Final set of threaded messages.
     */
    Set<EmailContainer> groupBySubject(Set<EmailContainer> rootSet) {
        Map<String, EmailContainer> subject_table = createSubjectTable(rootSet);

        Set<EmailContainer> finalSet = new HashSet<>();

        for (EmailContainer rootSetContainer : rootSet) {
            String rootSubject = rootSetContainer.getSimplifiedSubject();

            EmailContainer tableContainer = subject_table.get(rootSubject);
            if (tableContainer == null || tableContainer.equals(rootSetContainer)) {
                finalSet.add(rootSetContainer);
                continue;
            }

            // If both containers are dummy/empty append the children of one to the other
            if (tableContainer.getMessage() == null && rootSetContainer.getMessage() == null) {
                tableContainer.addChildren(rootSetContainer.getChildren());
                rootSetContainer.clearChildren();
                continue;
            }

            // one container is empty, but the other is not, make the non-empty one be a
            // child of the empty
            if ((tableContainer.getMessage() == null && rootSetContainer.getMessage() != null)
                    || (tableContainer.getMessage() != null && rootSetContainer.getMessage() == null)) {

                if (tableContainer.getMessage() == null) {
                    tableContainer.addChild(rootSetContainer);

                } else {
                    rootSetContainer.addChild(tableContainer);
                    subject_table.remove(rootSubject, tableContainer);
                    subject_table.put(rootSubject, rootSetContainer);

                    finalSet.add(rootSetContainer);
                }

                continue;
            }

            // tableContainer is non-empty and it's message's subject does not begin
            // with 'RE:' but rootSetContainer's message does begin with 'RE:', then
            // make rootSetContainer a child of tableContainer
            if (tableContainer.getMessage() != null
                    && !tableContainer.isReplySubject()
                    && rootSetContainer.isReplySubject()) {
                tableContainer.addChild(rootSetContainer);
                continue;
            }

            // If table container is non-empy, and table container's subject does 
            // begin with 'RE:', but rootSetContainer does not start with 'RE:'
            // make tableContainer a child of rootSetContainer
            if (tableContainer.getMessage() != null
                    && tableContainer.isReplySubject()
                    && !rootSetContainer.isReplySubject()) {
                rootSetContainer.addChild(tableContainer);
                subject_table.put(rootSubject, rootSetContainer);
                finalSet.add(rootSetContainer);
                continue;
            }

            // rootSetContainer and tableContainer either both have 'RE' or
            // don't.  Create a new dummy container with both containers as
            // children.
            EmailContainer newParent = new EmailContainer();
            newParent.addChild(tableContainer);
            newParent.addChild(rootSetContainer);
            subject_table.remove(rootSubject, tableContainer);
            subject_table.put(rootSubject, newParent);

            finalSet.add(newParent);
        }
        return finalSet;
    }

    /**
     * Creates a Hashtable of Container unique subjects. There will be one
     * Container subject pair for each unique subject.
     * 
     * @param rootSet The set of "root" Containers
     *
     * @return The subject hashtable
     */
    Map<String, EmailContainer> createSubjectTable(Set<EmailContainer> rootSet) {
        HashMap<String, EmailContainer> subject_table = new HashMap<>();

        for (EmailContainer rootSetContainer : rootSet) {
            String subject = "";
            boolean reSubject = false;

            if (rootSetContainer.hasMessage()) {
                subject = rootSetContainer.getMessage().getSimplifiedSubject();
                reSubject = rootSetContainer.getMessage().isReplySubject();
            } else if (rootSetContainer.hasChildren()) {
                Iterator<EmailContainer> childrenIterator = rootSetContainer.getChildren().iterator();
                while (childrenIterator.hasNext()) {
                    EmailMessage childMessage = childrenIterator.next().getMessage();
                    if (childMessage != null) {
                        subject = childMessage.getSimplifiedSubject();
                        if (!subject.isEmpty()) {
                            reSubject = childMessage.isReplySubject();
                            break;
                        }
                    }
                }
            }

            if (subject == null || subject.isEmpty()) {
                continue;  // Give up on this container
            }

            EmailContainer tableContainer = subject_table.get(subject);
            
//      A container will be added to the table, if a container for its "simplified" subject
//      does not currently exist in the table.  Or  if there is more than one container with the same
//      subject, but one is an "empty container" the empty one will be added 
//      the table or in the one in the table has "RE" in the subject it will be replaced
//      by the one that does not have "RE" in the subject (if it exists)
//     
            if (tableContainer == null || 
                    (tableContainer.getMessage() != null && rootSetContainer.getMessage() == null) ||
                    (!reSubject && (tableContainer.getMessage() != null && tableContainer.getMessage().isReplySubject()))) { 
                subject_table.put(subject, rootSetContainer);
            }

        }

        return subject_table;
    }
    
    /**
     * Assign "threadIDs" for each thread.  It is assumed that each member of
     * containerSet is a unique message thread.
     * 
     * ThreadIDs will only be unique between runs if "IDPrefix" is unique between
     * runs of the algorithm. 
     * 
     * @param containerSet A set of "root" containers
     * 
     * @param IDPrefix A string to make the threadIDs unique.
     */
    private void assignThreadIDs(Set<EmailContainer> containerSet) {
        for(EmailContainer container: containerSet) {
            // Generate a threadID
            String threadID = UUID.randomUUID().toString();
            // Add the IDs to this thread
            addThreadID(container, threadID);
        }
    }
    
    /**
     * Recursively walk container's children adding the thread ID to 
     * the EmailMessage objects.
     * 
     * @param container The root container of a set of related container objects
     * @param threadID  The String to assign as the "threadId" for this set of 
     *                  messages
     */
    private void addThreadID(EmailContainer container, String threadID) {
        if(container == null) {
            return;
        }
        
        EmailMessage message = container.getMessage();
        if(message != null) {
            message.setMessageThreadID(threadID);
        }
        
        if(container.hasChildren()) {
            for(EmailContainer child: container.getChildren()) {
                addThreadID(child, threadID);
            }
        }
    }

    /**
     * The container object is used to wrap and email message and track the
     * messages parent and child messages.
     */
    final class EmailContainer {

        private EmailMessage message;
        private EmailContainer parent;
        private Set<EmailContainer> children;

        /**
         * Constructs an empty container.
         */
        EmailContainer() {
            // This constructor is intentially empty to allow for the creation of 
            // an EmailContainer without a message
        }

        /**
         * Constructs a new Container object with the given EmailMessage.
         * 
         * @param message Returns the message, or null if one was not set
         */
        EmailContainer(EmailMessage message) {
            this.message = message;
        }

        /**
         * Returns the EmailMessage object.
         *
         * @return Then EmailMessage object or null if one was not set
         */
        EmailMessage getMessage() {
            return message;
        }

        /**
         * Set the Container EmailMessage object.
         *
         * @param message - The container EmailMessage
         */
        void setMessage(EmailMessage message) {
            this.message = message;
        }

        /**
         * Return whether or not this Container has a valid EmailMessage object.
         *
         * @return True if EmailMessage has been set otherwise false
         */
        boolean hasMessage() {
            return message != null;
        }

        /**
         * Returns the Simplified Subject (original subject without RE:) of the
         * EmailMessage or if this is an empty Container with Children, return
         * the simplified subject of one of the children.
         *
         * @return Simplified subject of this Container
         */
        String getSimplifiedSubject() {
            String subject = "";
            if (message != null) {
                subject = message.getSimplifiedSubject();
            } else if (children != null) {
                for (EmailContainer child : children) {
                    if (child.hasMessage()) {
                        subject = child.getSimplifiedSubject();
                    }

                    if (subject != null && !subject.isEmpty()) {
                        break;
                    }
                }
            }
            return subject;
        }

        /**
         * Simialar to getSimplifiedSubject, isReplySubject is a helper function
         * that will return the isReplySubject of the Containers message or if
         * this is an empty container, the state of one of the children.
         *
         * @return
         */
        boolean isReplySubject() {
            if (message != null) {
                return message.isReplySubject();
            } else if (children != null) {
                for (EmailContainer child : children) {
                    if (child.hasMessage()) {
                        boolean isReply = child.isReplySubject();

                        if (isReply) {
                            return isReply;
                        }
                    }
                }
            }

            return false;
        }

        /**
         * Returns the parent Container of this Container.
         *
         * @return The Container parent or null if one is not set
         */
        EmailContainer getParent() {
            return parent;
        }

        /**
         * Sets the given container as the parent of this object.
         * 
         * @param container - the object to set as the parent
         */
        void setParent(EmailContainer container) {
            parent = container;
        }

        /**
         *  Returns true if a parent object is current set.
         * 
         * @return True if this container has a parent otherwise false
         */
        boolean hasParent() {
            return parent != null;
        }

        /**
         * Adds the specified Container to the list of children.
         *
         * @param child - the Container to add to the child list
         *
         * @return true, if the element was added to the children list
         */
        boolean addChild(EmailContainer child) {
            if (children == null) {
                children = new HashSet<>();
            }

            return children.add(child);
        }

        /**
         * Adds to the list of children all of the elements that are contained
         * in the specified collection.
         *
         * @param children - set containing the Containers to be added to the
         *                 list of children
         *
         * @return true if the list of children was changed as a result of this
         *         call
         */
        boolean addChildren(Set<EmailContainer> children) {
            if (children == null || children.isEmpty()) {
                return false;
            }

            if (this.children == null) {
                this.children = new HashSet<>();
            }

            return this.children.addAll(children);
        }

        /**
         * Removes from the children list all of the elements that are contained
         * in the specified collection.
         *
         * @param children - set containing the elements to be removed from the
         *                 list of children
         *
         * @return true if the set was changed as a result of this call
         */
        boolean removeChildren(Set<EmailContainer> children) {
            if (children != null) {
                return this.children.removeAll(children);
            }

            return false;
        }

        /**
         * Clears the Containers list of children.
         *
         */
        void clearChildren() {
            if( children != null ) {
                children.clear();
            }
        }

        /**
         * Removes the given container from the list of children.
         *
         * @param child - the container to remove from the children list
         *
         * @return - True if the given container successfully removed from the
         *         list of children
         */
        boolean removeChild(EmailContainer child) {
            if(children != null) {
                return children.remove(child);
            } else {
                return false;
            }
        }

        /**
         * Returns whether or not this container has children.
         *
         * @return True if the child list is not null or empty
         */
        boolean hasChildren() {
            return children != null && !children.isEmpty();
        }

        /**
         * Returns the list of children of this container.
         *
         * @return The child list or null if a child has not been added.
         */
        Set<EmailContainer> getChildren() {
            return children;
        }

        /**
         * Search all of this containers children to make sure that the given
         * container is not a related.
         *
         * @param container - the container object to search for
         *
         * @return True if the given container is in the child tree of this
         *         container, false otherwise.
         */
        boolean isChild(EmailContainer container) {   
            return isChild(container, new HashSet<>());
        }   
        
        /**
         * Search all of this containers children to make sure that the given
         * container is not a related.
         *
         * @param container - the container object to search for
         * @param processedContainers - every container seen while doing this isChild check (to prevent possible loop)
         *
         * @return True if the given container is in the child tree of this
         *         container, false otherwise.
         */
        private boolean isChild(EmailContainer container, Set<EmailContainer> processedContainers) {
            processedContainers.add(this);
            if (children == null || children.isEmpty()) {
                return false;
            } else if (children.contains(container)) {
                return true;
            } else {
                for (EmailContainer child : children) {
                    // Prevent an infinite recursion by making sure we haven't already
                    // run isChild() on this child
                    if ((!processedContainers.contains(child)) && child.isChild(container, processedContainers)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }
}

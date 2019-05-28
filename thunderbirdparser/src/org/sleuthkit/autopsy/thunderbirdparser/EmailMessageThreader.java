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

/**
 * Given a list of email messages arranges the message into threads using the message
 * reference lists.
 * 
 * This threader is based heavely off of the algorithum found at <a href="https://www.jwz.org/doc/threading.html">
 * "message threading." by Jamie Zawinski</a>
 * 
 */
final class EmailMessageThreader {
    
    private int bogus_id_count = 0;

    public Set<Container> threadMessages(List<EmailMessage> emailMessages) {
        HashMap<String, Container> id_table = createIDTable(emailMessages);
        Set<Container> rootSet = getRootSet(id_table);
        
        pruneEmptyContainers(rootSet);
        
        Set<Container> finalSet = groupBySubject(rootSet);

        printContainerSet(finalSet, "");
        
        return finalSet;
    }

    /**
     * Walks the list of emailMessages creating a Container object for each 
     * unique message ID found.  Adds the emailMessage to the container where
     * possible.
     * 
     * @param emailMessages
     * @return 
     */
    private HashMap<String, Container> createIDTable(List<EmailMessage> emailMessages) {
        HashMap<String, Container> id_table = new HashMap<>();
        
        for(EmailMessage message: emailMessages) {
            String messageID = message.getMessageID();
            
            // Check the id_table for an existing Container for message-id
            Container container = id_table.get(messageID);
            
            // An existing container for message-id was found
            if(container != null) {
                // If the existing Container has a message already assocated with it
                // emailMessage maybe a duplicate, so we don't lose the existance of 
                // the duplicate message assign it a bogus message-id
                if(container.hasMessage()) {
                    messageID = String.format("<Bogus-id: %d >", bogus_id_count++);
                    container = null;
                } else {
                    container.setMessage(message);
                }
            } 
            
            if(container == null) {
                container = new Container(message);
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
     * @param message The current email messags
     * @param container Container object for message
     * @param id_table Hashtable of known message-id\container pairs
     */
    void processMessageReferences(EmailMessage message, Container container, Map<String, Container> id_table) {
        List<String> referenceList = message.getReferences();
        
        // Make sure the inReplyToID is in the list of references
        String inReplyToID = message.getInReplyToID();
        if(inReplyToID != null && !inReplyToID.isEmpty()) {
            if(referenceList == null) {
                referenceList = new ArrayList<>();
            }
            
            referenceList.add(inReplyToID);
        }
        
        // No references, nothing to do
        if(referenceList == null) {
            return;
        }

        Container parent_ref = null;
        Container ref;

        for(String refID: referenceList){
            // Check id_table to see if there is already a container for this
            // reference id, if not create a new Container and add to table
            ref = id_table.get(refID);

            if(ref == null ) {
                ref = new Container();
                id_table.put(refID, ref);
            }

            // Set the parent\child relationship between parent_ref and ref
            if (parent_ref != null
                    && !ref.hasParent()
                    && parent_ref != ref
                    && !parent_ref.isChild(ref)) {
                ref.setParent(parent_ref);
                parent_ref.addChild(ref);
            }

            parent_ref = ref;
        }
        
        // If the parent_ref and container are already linked, don't change 
        // anything
        if (parent_ref != null
            && (parent_ref == container
            || container.isChild(parent_ref))) {
            parent_ref = null;
        }

        // If container already has a parent, the parent was assumed based on
        // the list of references from another message.  parent_ref will be
        // the real parent of container so throw away the old parent and set a
        // new one.
        if(container.hasParent()) {
           container.getParent().removeChild(container);
           container.setParent(null);
        }

        if(parent_ref != null) {
            container.setParent(container);
            parent_ref.addChild(container);
        }
    }
    
    /**
     * Creates a set of root container messages from the message-ID hashtable. 
     * A root Container is container that does not have a parent container.
     * 
     * @param id_table Table of all known Containers
     * 
     * @return A set of the root containers.
     */
    Set<Container> getRootSet(HashMap<?, Container> id_table) {
        HashSet<Container> rootSet = new HashSet<>();
        
        id_table.values().stream().filter((container) -> 
                (!container.hasParent())).forEachOrdered((container) -> {
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
    void pruneEmptyContainers(Set<Container> containerSet) {
        containerSet.forEach((container) -> {
            if(!container.hasMessage() && !container.hasChildren()) {
                containerSet.remove(container);
            } else {
                pruneChildren(container);
            }
        });
    }
    
    /**
     * Recursively work through the list of parent's children removing 
     * empy containers.
     * 
     * @param parent 
     */
    void pruneChildren(Container parent) {
        if(parent == null) {
            return; 
        }
        
        Set<Container> children = parent.getChildren();
        Container grandParent = parent.getParent();
        
        if (children == null) {
            return;
        }

        children.stream().map((child) -> {
            // Parent is an empty container.  Reparent the children to their 
            // grandparent.
            if (!parent.hasMessage() && grandParent != null) {
                child.setParent(grandParent);
                grandParent.addChild(child);
                grandParent.removeChild(parent);
                parent.setParent(null);
                parent.clearChildren();
            }
            return child;
        }).forEachOrdered((child) -> {
            pruneChildren(child);
        });
    }
    
    /**
     * Now that the emails are grouped together by references\message ID take another
     * pass through and group together messages with the same simplified subject.
     * 
     * This may cause "root" messages with identical subjects to get grouped 
     * together as children of an empty container.  The code that uses the 
     * thread information can decide what to do in that sisiuation as those message
     * maybe part of a common thread or maybe their own unique messages.
     * 
     * @param rootSet 
     * @return Final set of threaded messages.
     */
    Set<Container> groupBySubject(Set<Container> rootSet) {
        HashMap<String, Container> subject_table = createSubjectTable(rootSet);

        Set<Container> finalSet = new HashSet<>();

        for (Container rootSetContainer : rootSet) {
            String rootSubject = rootSetContainer.getSimplifiedSubject();
            
            Container tableContainer = subject_table.get(rootSubject);
            if(tableContainer == null || tableContainer == rootSetContainer) {
                finalSet.add(rootSetContainer);
                continue;
            }
            
            // If both containers are dummy/empty append the children of one to the other
            if(tableContainer.getMessage() == null && rootSetContainer.getMessage() == null) {
                tableContainer.addChildren(rootSetContainer.getChildren());
                rootSetContainer.clearChildren();
                continue;
            }
            
            // one container is empty, but the other is not, make the non-empty one be a
            // child of the empty
            if( (tableContainer.getMessage() == null && rootSetContainer.getMessage() != null) || 
                    (tableContainer.getMessage() != null && rootSetContainer.getMessage() == null)){
            
                if(tableContainer.getMessage() == null) {
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
            if(tableContainer.getMessage() != null && 
                    !tableContainer.isReplySubject() &&
                    rootSetContainer.isReplySubject()) {
                tableContainer.addChild(rootSetContainer);
                continue;
            }
            
            // If table container is non-empy, and table container's subject does 
            // begin with 'RE:', but rootSetContainer does not start with 'RE:'
            // make tableContainer a child of rootSetContainer
            if(tableContainer.getMessage() != null && 
                    tableContainer.isReplySubject() &&
                    !rootSetContainer.isReplySubject()) {
                rootSetContainer.addChild(tableContainer);
                subject_table.put(rootSubject, rootSetContainer);
                finalSet.add(rootSetContainer);
                continue;
            }
            
            // rootSetContainer and tableContainer either both have 'RE' or
            // don't.  Create a new dummy container with both containers as
            // children.

            Container newParent = new Container();
            newParent.addChild(tableContainer);
            newParent.addChild(rootSetContainer);
            subject_table.remove(rootSubject, tableContainer);
            subject_table.put(rootSubject, newParent);

            finalSet.add(newParent);
        }
        return finalSet;
    }
    
    /**
     * Creates a Hashtable of Container and subjects. There will be one Container 
     * subject pair for each unique subject.
     * @param rootSet The set of "root" Containers
     * @return 
     */
    HashMap<String, Container> createSubjectTable(Set<Container> rootSet) {
        HashMap<String, Container> subject_table = new HashMap<>();

        for (Container rootSetContainer : rootSet) {
            String subject = "";
            boolean reSubject = false;

            if (rootSetContainer.hasMessage()) {
                subject = rootSetContainer.getMessage().getSimplifiedSubject();
                reSubject = rootSetContainer.getMessage().isReplySubject();
            } else if(rootSetContainer.hasChildren()){
                Iterator<Container> childrenIterator = rootSetContainer.getChildren().iterator();
                while(childrenIterator.hasNext()) {
                    EmailMessage childMessage = childrenIterator.next().getMessage();
                    if(childMessage != null) {
                        subject = childMessage.getSimplifiedSubject();
                        if(!subject.isEmpty()) {
                            reSubject = childMessage.isReplySubject();
                            break;
                        }
                    }
                }
            }

            if (subject.isEmpty()) {
                continue;  // Give up on this container
            }
            
            Container tableContainer = subject_table.get(subject);
            
            if(tableContainer == null || // Not in table
                    (tableContainer.getMessage() != null && rootSetContainer.getMessage() == null) || // One in the table is empty, but current is not
                    (!reSubject && (tableContainer.getMessage() != null  && tableContainer.getMessage().isReplySubject()))) { //current doesn't have RE in it, use current instead of the one in the table
                subject_table.put(subject, rootSetContainer);
            } 

        }
        
        return subject_table;
    }
    
    /**
     * Prints a set of containers and their children. 
     * 
     * @param containerSet Set of containers to print out
     * @param prefix A prefix for each line to show child depth.
     */    
    private void printContainerSet(Set<Container> containerSet, String prefix) {
        containerSet.stream().map((container) -> {
            if(container.getMessage() != null) {
                System.out.println(prefix + container.getMessage().getSubject());
            } else {
                System.out.println("<Empty Container>");
            }
            return container;
        }).filter((container) -> (container.hasChildren())).forEachOrdered((container) -> {
            printContainerSet(container.getChildren(), prefix+ "\t");
        });
    }
    
    /**
     * The container object is used to wrap and email message and track the
     * messages parent and child messages.
     */
    final class Container{
        private EmailMessage message;
        private Container parent;
        private Set<Container> children; 
        
        /**
         * Constructs an empy container.
         */
        Container() {}
      
        /**
         * 
         * @param message 
         */
        Container(EmailMessage message) {
            this.message = message;
        }
        
        /**
         * Returns the EmailMessage object
         * 
         * @return 
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
            if(message != null) {
                subject =  message.getSimplifiedSubject();
            } else if(children != null) {
                for(Container child: children) {
                    if(child.hasMessage()) {
                        subject = child.getSimplifiedSubject();
                    }
                    
                    if(subject != null && !subject.isEmpty()) {
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
            if(message != null) {
                return message.isReplySubject();
            } else if(children != null) {
                 for(Container child: children) {
                    if(child.hasMessage()) {
                        boolean isReply = child.isReplySubject();
                        
                        if(isReply) {
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
        Container getParent() {
            return parent;
        }
        
        /**
         * 
         * @param container 
         */
        void setParent(Container container) {
            parent = container;
        }
        
        /**
         * 
         * @return 
         */
        boolean hasParent() {
            return parent != null;
        }
 
        /**
         * 
         * @param child
         * @return 
         */
        boolean addChild(Container child) {
            if(children == null) {
                children = new HashSet<>(); 
            }
            
            return children.add(child);
        }
        
        /**
         * 
         * @param children
         * @return 
         */
        boolean addChildren(Set<Container> children) {
            if(children == null || children.isEmpty()) {
                return false;
            }
            
            if(this.children == null) {
                this.children = new HashSet<>(); 
            }
            
            return this.children.addAll(children);
        }
        
        /**
         * 
         */
        void clearChildren() {
            children.clear();
        }
        
        /**
         * 
         * @param child
         * @return 
         */
        boolean removeChild(Container child) {
            return children.remove(child);
        }
        
        /**
         * 
         * @return 
         */
        boolean hasChildren() {
            return children != null && !children.isEmpty();
        }
        
        /**
         * 
         * @return 
         */
        Set<Container> getChildren() {
            return children;
        }
        
        /**
         * 
         * @return 
         */
//        boolean hasSiblings() {
//            return children == null ? false : (children.size() > 1);
//        }
        
        /**
         * 
         * @param container
         * @return 
         */
        boolean isChild(Container container) {
            if(children == null || children.isEmpty()) {
                return false;
            } else if(children.contains(container)) {
                return true;
            } else {
                if (children.stream().anyMatch((child) -> (child.isChild(container)))) {
                    return true;
                }
                return false;
            }
        }
    }
}

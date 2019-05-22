/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.thunderbirdparser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * 
 */
final class EmailMessageThreader {

    public void threadMessages(List<EmailMessage> emailMessages) {
        HashMap<String, Container> id_table = createIDTable(emailMessages);
        Set<Container> rootSet = getRootSet(id_table);
        
        for(Container container: rootSet) {
            System.out.println(container.getMessage().getSubject());
            
            Container child = container.getChild();
            String tabs = "\t";
            while(child != null) {
                System.out.println(tabs + child.getMessage().getSubject());
                
                child = child.getChild();
                tabs = tabs + "\t";
            }
        }
    }
    
    private HashMap<String, Container> createIDTable(List<EmailMessage> emailMessages) {
        HashMap<String, Container> id_table = new HashMap<>();
        
        for(EmailMessage message: emailMessages) {
            String messageID = message.getMessageID();
            
            Container container = id_table.get(messageID);
            if(container == null) {
                container = new Container(message);
                id_table.put(messageID, container);
            } else {
                if(container.getMessage() == null) {
                    container.setMessage(message);
                }
            }
            
           
            
            List<String> referenceList = message.getReferences();
            
            if(referenceList != null) {
                Container parent = null;
                Container current;
            
                for(String refID:  referenceList){
                    current = id_table.get(refID);

                    if(current == null ) {
                        current = new Container();
                        id_table.put(refID, current);
                    }

                    // If a link already exists don't change it;

                    if(parent != null) {
                        parent.setChild(current);

                        Container grandparent = parent.getParent();
                        if(grandparent != null) {
                            grandparent.setNextSibling(current);
                        }

                    } 

                    current.setParent(parent);
                    parent = current;
                }
                
                // 
                parent = id_table.get(referenceList.get(referenceList.size() - 1));
                Container oldParent = container.getParent();
                if(oldParent != parent) {
                    container.setParent(parent);
                    parent.setChild(container);
                    parent.setNextSibling(container.getChild());
                    
                    if(oldParent != null) {
                        oldParent.setChild(null);
                        oldParent.setNextSibling(null);
                    }
                }
            }
        }
        
        return id_table;
    }
    
    Set<Container> getRootSet(HashMap<String, Container> id_table) {
        HashSet<Container> rootSet = new HashSet<>();
        
        id_table.values().stream().filter((container) -> 
                (!container.hasParent())).forEachOrdered((container) -> {
            rootSet.add(container);
        });
        
        return rootSet;
    }
    
    void pruneEmptyContainers(Set<Container> rootSet) {
        for(Container container: rootSet) {
            if(!container.hasMessage()) { // Container doesn't have message
                if(!container.hasChild()) {
                    rootSet.remove(container);
                } else {
                    if(!container.hasNextSibling()) {   // Container has no message, but one child
                        rootSet.remove(container);      // prompot the child to the rootSet
                        rootSet.add(container.child);
                    } else {
                        pruneChildren(container);
                    }
                }
            }
        }
    }
    
    void pruneChildren(Container container) {
        if(!container.hasMessage()) {
            if(container.hasChild()) {
                Container parent = container.getParent();
                parent.setChild(container.getChild());
                parent.setNextSibling(container.getNextSibling());
                container.getChild().setParent(parent);
                
                pruneChildren(container.child);
            }
        }
    }
    
    final class Container{
        private EmailMessage message;
        private Container parent;
        private Container child; // firt Child
        private Container next;  // next element in siblint list or null;
        
        Container() {}
      
        Container(EmailMessage message) {
            this.message = message;
        }
        
        EmailMessage getMessage() {
            return message;
        }
        
        void setMessage(EmailMessage message) {
            this.message = message;
        }
        
        boolean hasMessage() {
            return message != null;
        }
        
        Container getParent() {
            return parent;
        }
        
        void setParent(Container container) {
            parent = container;
        }
        
        boolean hasParent() {
            return parent != null;
        }
        
        Container getChild() { 
            return child;
        }
        
        void setChild(Container container) {
            child = container;
        }
        
        boolean hasChild() {
            return child != null;
        }
        
        Container getNextSibling() {
            return next;
        }
        
        void setNextSibling(Container container) {
            next = container;
        }
        
        boolean hasNextSibling() {
            return next != null;
        }
    }
}

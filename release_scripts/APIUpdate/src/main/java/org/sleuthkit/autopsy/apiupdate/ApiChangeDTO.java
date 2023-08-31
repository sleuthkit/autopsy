/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.sleuthkit.autopsy.apiupdate;

import japicmp.model.JApiChangeStatus;
import java.util.Optional;

/**
 *
 * @author gregd
 */
public class ApiChangeDTO {
    //NEW, REMOVED, UNCHANGED, MODIFIED

    public interface PublicApiChangeable {

        PublicApiChangeType getChangeType();
    }

    public static class ClassChangeDTO {

        private final JApiChangeStatus changeStatus;
        private final Optional<String> oldDeclaration;
        private final Optional<Long> oldSerialId;
        
        private final Optional<String> newDeclaration;
        private final Optional<Long> newSerialId;

        public ClassChangeDTO(JApiChangeStatus changeStatus, Optional<String> oldDeclaration, Optional<Long> oldSerialId, Optional<String> newDeclaration, Optional<Long> newSerialId) {
            this.changeStatus = changeStatus;
            this.oldDeclaration = oldDeclaration;
            this.oldSerialId = oldSerialId;
            this.newDeclaration = newDeclaration;
            this.newSerialId = newSerialId;
        }

        public JApiChangeStatus getChangeStatus() {
            return changeStatus;
        }

        public Optional<String> getOldDeclaration() {
            return oldDeclaration;
        }

        public Optional<Long> getOldSerialId() {
            return oldSerialId;
        }

        public Optional<String> getNewDeclaration() {
            return newDeclaration;
        }

        public Optional<Long> getNewSerialId() {
            return newSerialId;
        }
        
        
    }

//    public static class SuperclassChangeDTO {
//
//        private final Optional<String> oldFullyQualifiedClassName;
//        private final Optional<String> newFullyQualifiedClassName;
//
//        public SuperclassChangeDTO(Optional<String> oldFullyQualifiedClassName, Optional<String> newFullyQualifiedClassName) {
//            this.oldFullyQualifiedClassName = oldFullyQualifiedClassName;
//            this.newFullyQualifiedClassName = newFullyQualifiedClassName;
//        }
//
//        public Optional<String> getOldFullyQualifiedClassName() {
//            return oldFullyQualifiedClassName;
//        }
//
//        public Optional<String> getNewFullyQualifiedClassName() {
//            return newFullyQualifiedClassName;
//        }
//
//    }
//
//    public static class InterfaceChangeDTO {
//
//        private final JApiChangeStatus changeStatus;
//        private final String fullyQualifiedName;
//
//        public InterfaceChangeDTO(JApiChangeStatus changeStatus, String fullyQualifiedName) {
//            this.changeStatus = changeStatus;
//            this.fullyQualifiedName = fullyQualifiedName;
//        }
//
//        public JApiChangeStatus getChangeStatus() {
//            return changeStatus;
//        }
//
//        public String getFullyQualifiedName() {
//            return fullyQualifiedName;
//        }
//
//    }
    public static class MethodChangeDTO {

        private final JApiChangeStatus changeStatus;
        private final Optional<String> oldMethodDeclaration;
        private final Optional<String> newMethodDeclaration;

        public MethodChangeDTO(JApiChangeStatus changeStatus, Optional<String> oldMethodDeclaration, Optional<String> newMethodDeclaration) {
            this.changeStatus = changeStatus;
            this.oldMethodDeclaration = oldMethodDeclaration;
            this.newMethodDeclaration = newMethodDeclaration;
        }

        public JApiChangeStatus getChangeStatus() {
            return changeStatus;
        }

        public Optional<String> getOldMethodDeclaration() {
            return oldMethodDeclaration;
        }

        public Optional<String> getNewMethodDeclaration() {
            return newMethodDeclaration;
        }

    }

    public static class FieldChangeDTO {

        private final JApiChangeStatus changeStatus;
        private final Optional<String> oldFieldDeclaration;
        private final Optional<String> newFieldDeclaration;

        public FieldChangeDTO(JApiChangeStatus changeStatus, Optional<String> oldFieldDeclaration, Optional<String> newFieldDeclaration) {
            this.changeStatus = changeStatus;
            this.oldFieldDeclaration = oldFieldDeclaration;
            this.newFieldDeclaration = newFieldDeclaration;
        }

        public JApiChangeStatus getChangeStatus() {
            return changeStatus;
        }

        public Optional<String> getOldFieldDeclaration() {
            return oldFieldDeclaration;
        }

        public Optional<String> getNewFieldDeclaration() {
            return newFieldDeclaration;
        }

    }

}

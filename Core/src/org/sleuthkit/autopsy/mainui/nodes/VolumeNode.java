///*
// * Autopsy Forensic Browser
// *
// * Copyright 2011-2021 Basis Technology Corp.
// * Contact: carrier <at> sleuthkit <dot> org
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.sleuthkit.autopsy.mainui.nodes;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import javax.swing.Action;
//import org.openide.nodes.AbstractNode;
//import org.openide.nodes.Sheet;
//import org.openide.util.NbBundle;
//import org.sleuthkit.autopsy.coreutils.Logger;
//import org.sleuthkit.autopsy.datamodel.NodeProperty;
//import org.sleuthkit.autopsy.directorytree.ExplorerNodeActionVisitor;
//import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
//import org.sleuthkit.datamodel.Volume;
//import org.sleuthkit.autopsy.directorytree.FileSystemDetailsAction;
//import org.sleuthkit.datamodel.TskData;
//import org.sleuthkit.datamodel.TskData.TSK_VS_PART_FLAG_ENUM;
//
///**
// * This class is used to represent the "Node" for the volume. Its child is the
// * root directory of a file system
// */
//public class VolumeNodev2 extends AbstractNode {
//
//    private static final Logger logger = Logger.getLogger(VolumeNodev2.class.getName());
//
//    public static class VolumeTableDTO {
//
//        private final Volume volume;
//        private final long id;
//        private final long addr;
//        private final long start;
//        private final long length;
//        private final String description;
//        private final boolean contiguousVolume; // based on vol.getParent() != null && vol.getParent().getParent() instanceof Pool
//        private final TskData.TSK_VS_PART_FLAG_ENUM partitionFlag;  // see Volume.vsFlagToString
//
//        public VolumeTableDTO(Volume volume, long id, long addr, long start, long length, String description, boolean contiguousVolume, TSK_VS_PART_FLAG_ENUM partitionFlag) {
//            this.volume = volume;
//            this.id = id;
//            this.addr = addr;
//            this.start = start;
//            this.length = length;
//            this.description = description;
//            this.contiguousVolume = contiguousVolume;
//            this.partitionFlag = partitionFlag;
//        }
//
//        
//        public long getAddr() {
//            return addr;
//        }
//
//        public long getStart() {
//            return start;
//        }
//
//        public long getLength() {
//            return length;
//        }
//
//        public String getDescription() {
//            return description;
//        }
//
//        public boolean isContiguousVolume() {
//            return contiguousVolume;
//        }
//
//        public TSK_VS_PART_FLAG_ENUM getPartitionFlag() {
//            return partitionFlag;
//        }
//
//        public long getId() {
//            return id;
//        }
//
//        public Volume getVolume() {
//            return volume;
//        }
//
//    }
//
//    private final VolumeTableDTO volData;
//
//    /**
//     *
//     * @param vol underlying Content instance
//     */
//    public VolumeNodev2(VolumeTableDTO vol) {
//        super(ContentNodeUtilv2.getChildren(vol.getId()), ContentNodeUtilv2.getLookup(vol.getVolume()));
//        setIconBaseWithExtension("org/sleuthkit/autopsy/images/vol-icon.png");
//        setDisplayName(getDisplayName(vol));
//        setName(ContentNodeUtilv2.getContentName(vol.getId()));
//        this.volData = vol;
//
//    }
//
//    private String getDisplayName(VolumeTableDTO vol) {
//        // set name, display name, and icon
//        String volName = "vol" + Long.toString(vol.getAddr());
//        long end = vol.getStart() + (vol.getLength() - 1);
//        return vol.isContiguousVolume()
//                ? volName + " (" + vol.getDescription() + ": " + vol.getStart() + "-" + end + ")"
//                : volName + " (" + vol.getDescription() + ": " + vol.getStart() + ")";
//    }
//
//    /**
//     * Right click action for volume node
//     *
//     * @param popup
//     *
//     * @return
//     */
//    @Override
//    public Action[] getActions(boolean popup) {
//        List<Action> actionsList = new ArrayList<>();
//        actionsList.add(new FileSystemDetailsAction(volData.getVolume()));
//        actionsList.add(new NewWindowViewAction(
//                NbBundle.getMessage(this.getClass(), "VolumeNode.getActions.viewInNewWin.text"), this));
//        actionsList.addAll(ExplorerNodeActionVisitor.getActions(volData.getVolume()));
//        actionsList.add(null);
//        actionsList.addAll(Arrays.asList(super.getActions(true)));
//
//        return actionsList.toArray(new Action[actionsList.size()]);
//    }
//
//    @Override
//    protected Sheet createSheet() {
//        Sheet sheet = super.createSheet();
//        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
//        if (sheetSet == null) {
//            sheetSet = Sheet.createPropertiesSet();
//            sheet.put(sheetSet);
//        }
//
//        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.name.name"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.name.displayName"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.name.desc"),
//                this.getDisplayName()));
//        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.id.name"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.id.displayName"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.id.desc"),
//                volData.getAddr()));
//        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.startSector.name"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.startSector.displayName"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.startSector.desc"),
//                volData.getStart()));
//        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.lenSectors.name"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.lenSectors.displayName"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.lenSectors.desc"),
//                volData.getLength()));
//        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.description.name"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.description.displayName"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.description.desc"),
//                volData.getDescription()));
//        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.flags.name"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.flags.displayName"),
//                NbBundle.getMessage(this.getClass(), "VolumeNode.createSheet.flags.desc"),
//                Volume.vsFlagToString(volData.getPartitionFlag().getVsFlag())));
//
//        return sheet;
//    }
//}

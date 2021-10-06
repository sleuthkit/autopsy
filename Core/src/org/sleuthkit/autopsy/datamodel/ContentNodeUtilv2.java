/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.Content;

/**
 *
 * @author gregd
 */
public class ContentNodeUtilv2 {

    public static String getContentDisplayName(String fileName) {
        switch (fileName) {
            case "..":
                return DirectoryNode.DOTDOTDIR;
            case ".":
                return DirectoryNode.DOTDIR;
            default:
                return fileName;
        }
    }

    public static String getContentName(long objId) {
        return "content_" + Long.toString(objId);
    }

    public static Lookup getLookup(Content content) {
        return Lookups.fixed(content, new TskContentItem<>(content));
    }

    public static Children getChildren(long id) {
        throw new UnsupportedOperationException("Not supported...");
        //return Children.create(new ContentChildren(content), true);
    }
}

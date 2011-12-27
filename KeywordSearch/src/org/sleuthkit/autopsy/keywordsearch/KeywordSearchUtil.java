package org.sleuthkit.autopsy.keywordsearch;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskException;

public class KeywordSearchUtil {

    private static final Logger logger = Logger.getLogger(KeywordSearchUtil.class.getName());

    public static String buildDirName(FsContent f) {

        String dirName = null;
        StringBuilder dirNameB = new StringBuilder();
        try {

            Directory pd = f.getParentDirectory();

            while (pd != null && pd.isRoot() == false) {
                dirNameB.insert(0, "/");
                dirNameB.insert(0, pd.getName());
                pd = pd.getParentDirectory();
            }
            dirNameB.insert(0, "/");

        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error getting path for fscontent id: " + Long.toString(f.getId()), ex);
        } finally {
            dirName = dirNameB.toString();
        }
        return dirName;
    }
}

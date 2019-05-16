/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.newpackage;

/**
 *
 */
class FileSearchData {
    
    enum  Frequency {
	UNIQUE(0, "Unique"),
	RARE(1, "Rare"),
	COMMON(2, "Common"),
	UNKNOWN(3, "Unknown");
        
        private final int ranking;
        private final String displayName;
        
        Frequency(int ranking, String displayName) {
            this.ranking = ranking;
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }

    enum FileSize {
        XL(0, 1000, -1, "1GB+"),
        LARGE(1, 200, 1000, "200MB - 1 GB"),
        MEDIUM(2, 50, 200, "50 - 200MB"),
        SMALL(3, 0, 50, "Under 50MB");

        private final int ranking;
        private final long minBytes; // Note that the size must be greater than this to match
        private final long maxBytes;
        private final String displayName;  
        private final static long BYTES_PER_MB = 1048576;
        final static long NO_MAXIMUM = -1;
        
        FileSize(int ranking, long minMB, long maxMB, String displayName) {
            this.ranking = ranking;
            this.minBytes = minMB * BYTES_PER_MB ;
            if (maxMB >= 0) {
                this.maxBytes = maxMB * BYTES_PER_MB;
            } else {
                this.maxBytes = NO_MAXIMUM;
            }
            this.displayName = displayName;
        }
                   
        static FileSize fromSize(long size) {
            if (size > XL.minBytes) {
                return XL;
            } else if (size > LARGE.minBytes) {
                return LARGE;
            } else if (size > MEDIUM.minBytes) {
                return MEDIUM;
            } else {
                return SMALL;
            }
        }
        
        long getMaxBytes() {
            return maxBytes;
        }
        
        long getMinBytes() {
            return minBytes;
        }
    }
   
    
    
    private FileSearchData() {
        // Class should not be instantiated
    }
}

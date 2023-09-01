/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.sleuthkit.autopsy.apiupdate;

import java.util.Comparator;
import org.apache.commons.lang3.ObjectUtils;

/**
 *
 * @author gregd
 */
public enum PublicApiChangeType implements Comparator<PublicApiChangeType> {
    NONE(0), COMPATIBLE_CHANGE(1), INCOMPATIBLE_CHANGE(2);

    private int level;

    PublicApiChangeType(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public int compare(PublicApiChangeType o1, PublicApiChangeType o2) {
        o1 = ObjectUtils.defaultIfNull(o1, PublicApiChangeType.NONE);
        o2 = ObjectUtils.defaultIfNull(o2, PublicApiChangeType.NONE);
        return Integer.compare(o1.getLevel(), o2.getLevel());
    }
}

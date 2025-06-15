package org.chaiware.acommander.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class Drive extends Folder {
    private String letter;
    private String storeType;
    private long totalSpace;
    private long availableSpace;

    public long getUsedSpace() {
        return getTotalSpace() - getAvailableSpace();
    }

    @Override
    public String toString() {
        return super.toString();
    }
}

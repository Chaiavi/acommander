package org.chaiware.acommander.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WindowsFolder extends Folder {
    String name;

    @Override
    public String toString() {
        return super.toString();
    }
}

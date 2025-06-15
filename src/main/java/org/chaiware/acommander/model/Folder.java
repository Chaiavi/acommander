package org.chaiware.acommander.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(force = true)
@Data
public class Folder {
    String path;

    public Folder(String path) {
        setPath(path);
    }

    @Override
    public String toString() {
        return path;
    }
}

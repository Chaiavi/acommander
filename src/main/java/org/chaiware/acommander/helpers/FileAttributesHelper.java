package org.chaiware.acommander.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.ArrayList;
import java.util.List;

public class FileAttributesHelper {
    private static final Logger logger = LoggerFactory.getLogger(FileAttributesHelper.class);

    public ExistingAttributes readExistingAttributes(Path path) {
        try {
            DosFileAttributeView view = Files.getFileAttributeView(path, DosFileAttributeView.class);
            if (view != null) {
                java.nio.file.attribute.DosFileAttributes attrs = view.readAttributes();
                return new ExistingAttributes(attrs.isReadOnly(), attrs.isHidden(), attrs.isSystem(), attrs.isArchive());
            }
        } catch (Exception ex) {
            logger.debug("Failed reading DOS attributes for {}, using basic fallbacks", path, ex);
        }

        File file = path.toFile();
        return new ExistingAttributes(!file.canWrite(), file.isHidden(), false, false);
    }

    public void applyAttributesWithFallback(Path path, AttributeChangeRequest request) throws Exception {
        IOException javaError = null;
        try {
            applyWithJava(path, request);
            return;
        } catch (IOException ex) {
            javaError = ex;
            logger.debug("Java DOS attributes failed for {}, trying attrib fallback", path, ex);
        }

        if (applyWithAttrib(path, request)) {
            return;
        }

        if (javaError != null) {
            throw javaError;
        }
        throw new IOException("attrib fallback failed");
    }

    private void applyWithJava(Path path, AttributeChangeRequest request) throws IOException {
        DosFileAttributeView view = Files.getFileAttributeView(path, DosFileAttributeView.class);
        if (view == null) {
            throw new IOException("DOS attributes are not supported for this item");
        }
        applyAttr(view::setReadOnly, request.readOnly());
        applyAttr(view::setHidden, request.hidden());
        applyAttr(view::setSystem, request.system());
        applyAttr(view::setArchive, request.archive());
    }

    private boolean applyWithAttrib(Path path, AttributeChangeRequest request) throws IOException, InterruptedException {
        List<String> flags = request.toAttribFlags();
        if (flags.isEmpty()) {
            return true;
        }

        List<String> command = new ArrayList<>();
        command.add("attrib");
        command.addAll(flags);
        command.add(path.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exit = process.waitFor();
        return exit == 0;
    }

    private void applyAttr(AttributeSetter setter, boolean value) throws IOException {
        setter.set(value);
    }

    private interface AttributeSetter {
        void set(boolean value) throws IOException;
    }

    public record AttributeChangeRequest(boolean readOnly, boolean hidden, boolean system, boolean archive) {
        public List<String> toAttribFlags() {
            List<String> flags = new ArrayList<>();
            addFlag(flags, readOnly, "R");
            addFlag(flags, hidden, "H");
            addFlag(flags, system, "S");
            addFlag(flags, archive, "A");
            return flags;
        }

        private void addFlag(List<String> flags, boolean enabled, String code) {
            if (enabled) {
                flags.add("+" + code);
            } else {
                flags.add("-" + code);
            }
        }
    }

    public record ExistingAttributes(boolean readOnly, boolean hidden, boolean system, boolean archive) {
    }
}

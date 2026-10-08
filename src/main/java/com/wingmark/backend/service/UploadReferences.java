package com.wingmark.backend.service;

import java.util.Set;

/** Knows which stored upload filenames are still used by a bird log, a profile picture or a species image. */
public interface UploadReferences {

    /** Returns the filenames (as in {@code /uploads/<filename>}) that something still refers to. */
    Set<String> referencedFilenames();
}

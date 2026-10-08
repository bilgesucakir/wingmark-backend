package com.wingmark.backend.service;

/** Reads the licence, author and page address of a Wikimedia Commons file, so a species image is credited correctly. */
public interface CommonsService {

    /** The credit details to store with an image taken from Commons. */
    record CommonsFile(String licenseCode, String attribution, String sourceUrl) {
    }

    /**
     * Reads the details of the file whose Commons page address is given. Only free-to-reuse licences are accepted
     * (public domain, CC0, CC BY, CC BY-SA); anything else, a non-Commons address or a missing file is a 400.
     */
    CommonsFile describe(String fileUrl);
}

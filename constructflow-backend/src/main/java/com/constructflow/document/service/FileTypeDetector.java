package com.constructflow.document.service;

import com.constructflow.common.exception.BadRequestException;

import java.util.Locale;

/**
 * Identifies uploads from their first bytes ("magic numbers") instead of trusting the client's
 * {@code Content-Type} header or file extension, both of which an attacker controls. Only an
 * allow-list of document types is accepted.
 */
public final class FileTypeDetector {

    private FileTypeDetector() {
    }

    public record DetectedType(String contentType, String extension) {
    }

    public static DetectedType detect(byte[] content, String originalFilename) {
        String extension = extensionOf(originalFilename);
        if (startsWith(content, 0x25, 0x50, 0x44, 0x46, 0x2D)) {               // %PDF-
            return new DetectedType("application/pdf", "pdf");
        }
        if (startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new DetectedType("image/png", "png");
        }
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return new DetectedType("image/jpeg", "jpg");
        }
        if (startsWith(content, 0x50, 0x4B, 0x03, 0x04)) {                     // ZIP container (Office Open XML)
            if ("docx".equals(extension)) {
                return new DetectedType("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
            }
            if ("xlsx".equals(extension)) {
                return new DetectedType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
            }
        }
        throw new BadRequestException("UNSUPPORTED_FILE_TYPE", "file",
                "Only PDF, PNG, JPEG, DOCX and XLSX files are accepted.");
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

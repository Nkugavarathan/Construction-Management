package com.constructflow.document.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

/** Development storage on the local disk. Rejects any key that is not a server-generated path. */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Pattern SAFE_KEY = Pattern.compile("[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}");

    private final Path root;

    public LocalFileStorage(@Value("${app.storage.local-root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file", e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read file", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete file", e);
        }
    }

    private Path resolve(String key) {
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path resolved = root.resolve(key).normalize();
        // Defense in depth: even a valid-looking key must stay inside the storage root.
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}

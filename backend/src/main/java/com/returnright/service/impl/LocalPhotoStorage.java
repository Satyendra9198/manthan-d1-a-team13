package com.returnright.service.impl;

import com.returnright.service.PhotoStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Keeps photos on the local file system under a configured directory. */
@Service
public class LocalPhotoStorage implements PhotoStorage {

  private final Path rootDirectory;

  public LocalPhotoStorage(@Value("${returnright.photos.directory}") Path rootDirectory) {
    this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
  }

  @Override
  public void store(String key, byte[] content) {
    Path target = resolve(key);
    try {
      Files.createDirectories(target.getParent());
      Files.write(target, content, StandardOpenOption.CREATE_NEW);
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not store photo " + key, ex);
    }
  }

  @Override
  public Optional<byte[]> load(String key) {
    Path source = resolve(key);
    if (!Files.isRegularFile(source)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readAllBytes(source));
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not read photo " + key, ex);
    }
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not delete photo " + key, ex);
    }
  }

  /** Resolves the key and refuses anything that would land outside the root directory. */
  private Path resolve(String key) {
    Path target = rootDirectory.resolve(key).normalize();
    if (!target.startsWith(rootDirectory) || target.equals(rootDirectory)) {
      throw new IllegalArgumentException("Invalid photo key: " + key);
    }
    return target;
  }
}

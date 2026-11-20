package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.returnright.support.TestImages;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPhotoStorageTest {

  private static final String KEY = "returns/0001/photo-1.jpg";

  @TempDir Path rootDirectory;

  @Test
  void storedPhotoIsWrittenUnderTheRootDirectory() throws IOException {
    new LocalPhotoStorage(rootDirectory).store(KEY, TestImages.JPEG);

    assertThat(Files.readAllBytes(rootDirectory.resolve(KEY))).isEqualTo(TestImages.JPEG);
  }

  @Test
  void deletedPhotoIsRemoved() {
    LocalPhotoStorage storage = new LocalPhotoStorage(rootDirectory);
    storage.store(KEY, TestImages.JPEG);

    storage.delete(KEY);

    assertThat(rootDirectory.resolve(KEY)).doesNotExist();
  }

  @Test
  void deletingAMissingPhotoDoesNothing() {
    new LocalPhotoStorage(rootDirectory).delete(KEY);

    assertThat(rootDirectory.resolve(KEY)).doesNotExist();
  }

  @Test
  void storingTheSameKeyTwiceIsRefused() {
    LocalPhotoStorage storage = new LocalPhotoStorage(rootDirectory);
    storage.store(KEY, TestImages.JPEG);

    assertThatThrownBy(() -> storage.store(KEY, TestImages.PNG))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void keyThatEscapesTheRootDirectoryIsRefused() {
    LocalPhotoStorage storage = new LocalPhotoStorage(rootDirectory.resolve("photos"));

    assertThatThrownBy(() -> storage.store("../outside.jpg", TestImages.JPEG))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(rootDirectory.resolve("outside.jpg")).doesNotExist();
  }
}

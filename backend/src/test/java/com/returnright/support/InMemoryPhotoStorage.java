package com.returnright.support;

import com.returnright.service.PhotoStorage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Photo storage held in memory, so tests can see exactly what was stored or deleted. */
public class InMemoryPhotoStorage implements PhotoStorage {

  private final Map<String, byte[]> photos = new LinkedHashMap<>();

  @Override
  public void store(String key, byte[] content) {
    if (photos.putIfAbsent(key, content) != null) {
      throw new IllegalStateException("Photo already stored: " + key);
    }
  }

  @Override
  public void delete(String key) {
    photos.remove(key);
  }

  public Map<String, byte[]> storedPhotos() {
    return Map.copyOf(photos);
  }
}

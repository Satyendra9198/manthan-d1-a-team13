package com.returnright.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for a photo the customer attached to a return item. The bytes live in photo storage
 * under {@code storageKey}, which the server generates; the client's file name is never used.
 */
@Entity
@Table(name = "return_photo")
public class ReturnPhoto {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "return_item_id", nullable = false, updatable = false)
  private ReturnItem returnItem;

  @Column(name = "storage_key", nullable = false, unique = true, updatable = false)
  private String storageKey;

  @Column(name = "content_type", nullable = false, updatable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private long sizeBytes;

  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  protected ReturnPhoto() {}

  public ReturnPhoto(
      UUID id, String storageKey, String contentType, long sizeBytes, Instant uploadedAt) {
    this.id = id;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.uploadedAt = uploadedAt;
  }

  void attachTo(ReturnItem returnItem) {
    this.returnItem = returnItem;
  }

  public UUID getId() {
    return id;
  }

  public ReturnItem getReturnItem() {
    return returnItem;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }
}

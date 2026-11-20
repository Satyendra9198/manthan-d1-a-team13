package com.returnright.model.domain;

import com.returnright.model.enums.PhotoFormat;

/** A photo that passed every check, with the format detected from its bytes. */
public record ValidatedPhoto(PhotoFormat format, byte[] content) {}

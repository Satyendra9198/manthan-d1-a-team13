package com.returnright.model.domain;

/** A stored photo ready to be sent to the browser. */
public record PhotoContent(String contentType, byte[] content) {}

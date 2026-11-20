package com.returnright.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import com.returnright.support.TestImages;
import org.junit.jupiter.api.Test;

class PhotoFormatTest {

  @Test
  void jpegSignatureIsDetectedAsJpeg() {
    assertThat(PhotoFormat.detect(TestImages.JPEG)).contains(PhotoFormat.JPEG);
  }

  @Test
  void pngSignatureIsDetectedAsPng() {
    assertThat(PhotoFormat.detect(TestImages.PNG)).contains(PhotoFormat.PNG);
  }

  @Test
  void riffWebpContainerIsDetectedAsWebp() {
    assertThat(PhotoFormat.detect(TestImages.WEBP)).contains(PhotoFormat.WEBP);
  }

  @Test
  void pdfIsNotDetectedAsAnImage() {
    assertThat(PhotoFormat.detect(TestImages.PDF)).isEmpty();
  }

  @Test
  void contentShorterThanAnySignatureIsNotDetected() {
    assertThat(PhotoFormat.detect(new byte[] {(byte) 0xFF})).isEmpty();
  }
}

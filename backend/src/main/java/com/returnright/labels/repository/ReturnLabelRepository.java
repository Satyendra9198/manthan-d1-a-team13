package com.returnright.labels.repository;

import com.returnright.labels.entity.ReturnLabel;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.returnright.core.config.RrProperties;
import com.returnright.core.enums.LabelStatus;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface ReturnLabelRepository extends JpaRepository<ReturnLabel, UUID> {
    Optional<ReturnLabel> findByQrToken(String qrToken);

    @Query("select l from ReturnLabel l where l.returnId = ?1 order by l.issuedAt desc")
    List<ReturnLabel> findByReturn(UUID returnId);

    @Query("select l from ReturnLabel l where l.returnId in ?1")
    List<ReturnLabel> findByReturns(List<UUID> returnIds);
}

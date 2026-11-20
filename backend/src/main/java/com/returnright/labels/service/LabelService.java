package com.returnright.labels.service;

import com.returnright.labels.entity.ReturnLabel;
import com.returnright.labels.repository.ReturnLabelRepository;
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

/** Mock carrier labels with QR codes (T2, T4, T5, T6). The QR encodes only a random 128-bit token. */
@Service
public class LabelService {


    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TRACK_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ReturnLabelRepository repo;
    private final Clock clock;
    private final RrProperties props;

    public LabelService(ReturnLabelRepository repo, Clock clock, RrProperties props) {
        this.repo = repo;
        this.clock = clock;
        this.props = props;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReturnLabel issue(UUID returnId) {
        Instant now = clock.instant();
        byte[] token = new byte[16];
        RANDOM.nextBytes(token);
        String qr = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        StringBuilder tracking = new StringBuilder("MOCKSHIP-");
        for (int i = 0; i < 10; i++) {
            tracking.append(TRACK_ALPHABET.charAt(RANDOM.nextInt(TRACK_ALPHABET.length())));
        }
        return repo.save(new ReturnLabel(returnId, qr, tracking.toString(), now,
                now.plus(Duration.ofDays(props.labelTtlDays()))));
    }

    public Optional<ReturnLabel> current(UUID returnId) {
        return repo.findByReturn(returnId).stream().findFirst();
    }

    public Map<UUID, ReturnLabel> currentFor(List<UUID> returnIds) {
        Map<UUID, ReturnLabel> out = new java.util.HashMap<>();
        if (returnIds.isEmpty()) {
            return out;
        }
        for (ReturnLabel l : repo.findByReturns(returnIds)) {
            out.merge(l.getReturnId(), l, (a, b) -> a.getIssuedAt().isAfter(b.getIssuedAt()) ? a : b);
        }
        return out;
    }

    public Optional<ReturnLabel> byToken(String token) {
        return repo.findByQrToken(token);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markUsed(ReturnLabel label) {
        label.markUsed(clock.instant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void close(UUID returnId, LabelStatus status) {
        repo.findByReturn(returnId).stream().filter(l -> l.getStatus() == LabelStatus.ACTIVE)
                .forEach(l -> l.setStatus(status));
    }

    /** data:image/svg+xml;base64 QR for the label page (no file writes needed). */
    public static String qrSvgDataUri(String content) {
        try {
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            int w = m.getWidth();
            int h = m.getHeight();
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (m.get(x, y)) {
                        path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                    }
                }
            }
            String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" shape-rendering=\"crispEdges\">"
                    .formatted(w, h)
                    + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"" + path + "\"/></svg>";
            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        } catch (WriterException e) {
            throw new IllegalStateException("QR generation failed", e);
        }
    }
}

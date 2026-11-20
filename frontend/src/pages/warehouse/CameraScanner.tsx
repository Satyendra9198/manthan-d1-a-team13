import { Camera, CameraOff } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../components/ui/Dialog";
import { getDetectorCtor } from "../../lib/barcode";

/**
 * Camera QR scanning via the native BarcodeDetector (no extra dependency).
 * The keyboard-wedge / manual input stays the primary path; this is shown only where supported.
 */
export function CameraScanner({ open, onClose, onDetected }: { open: boolean; onClose: () => void; onDetected: (value: string) => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [error, setError] = useState<string | null>(null);
  const detectedRef = useRef(onDetected);

  useEffect(() => {
    detectedRef.current = onDetected;
  }, [onDetected]);

  useEffect(() => {
    if (!open) return;
    const Ctor = getDetectorCtor();
    if (!Ctor) return;
    let stream: MediaStream | null = null;
    let raf = 0;
    let stopped = false;
    const detector = new Ctor({ formats: ["qr_code"] });

    async function start() {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" }, audio: false });
        const v = videoRef.current;
        if (!v || stopped) return;
        v.srcObject = stream;
        await v.play();
        const tick = async () => {
          if (stopped || !videoRef.current) return;
          try {
            const codes = await detector.detect(videoRef.current);
            const first = codes[0]?.rawValue;
            if (first) {
              stopped = true;
              detectedRef.current(first);
              return;
            }
          } catch {
            /* frame not ready */
          }
          raf = requestAnimationFrame(() => void tick());
        };
        raf = requestAnimationFrame(() => void tick());
      } catch {
        setError("Camera unavailable. Allow camera access or use the scanner / keyboard instead.");
      }
    }
    void start();
    return () => {
      stopped = true;
      cancelAnimationFrame(raf);
      stream?.getTracks().forEach((t) => t.stop());
    };
  }, [open]);

  return (
    <Dialog
      open={open}
      onClose={() => {
        setError(null);
        onClose();
      }}
      title="Scan with camera"
      description="Point the camera at the QR code on the label."
    >
      {error ? (
        <div className="flex flex-col items-center gap-3 py-8 text-center text-sm text-fg-muted" role="alert">
          <CameraOff className="size-8" aria-hidden />
          {error}
        </div>
      ) : (
        <div className="relative overflow-hidden rounded-xl bg-black">
          <video ref={videoRef} className="aspect-[4/3] w-full object-cover" muted playsInline />
          <div className="pointer-events-none absolute inset-0 grid place-items-center">
            <div className="size-52 rounded-2xl border-4 border-white/80 shadow-[0_0_0_9999px_rgba(0,0,0,0.35)]" />
          </div>
          <div className="absolute bottom-2 left-2 flex items-center gap-1.5 rounded-full bg-black/60 px-2.5 py-1 text-xs text-white">
            <Camera className="size-3.5" aria-hidden /> Looking for a QR code…
          </div>
        </div>
      )}
    </Dialog>
  );
}

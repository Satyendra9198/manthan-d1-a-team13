import { useRef } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import type { ReturnTracking } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card } from './card'

const WAREHOUSE = ['ReturnRight Returns Centre', 'Plot 14, MIDC Bhiwandi', 'Thane, Maharashtra 421302']

const esc = (s: string) =>
  s.replace(/[<>&"]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;' })[c]!)

/** Mock printable label: an SVG file with the QR code, RMA and warehouse address. */
export function buildLabelSvg(t: ReturnTracking, qrSvg: string): string {
  const qr = qrSvg.replace('<svg', '<svg x="24" y="96" width="168" height="168"')
  const lines = [
    `<text x="24" y="40" font-size="20" font-weight="700">ReturnRight · Return label</text>`,
    `<text x="24" y="68" font-size="15">${esc(t.rmaNumber)} · ${esc(t.item.name)}</text>`,
    `<text x="216" y="120" font-size="13" fill="#555">Ship to</text>`,
    ...WAREHOUSE.map((l, i) => `<text x="216" y="${142 + i * 20}" font-size="15">${esc(l)}</text>`),
    `<text x="24" y="290" font-size="13" fill="#555">${esc(t.qrCode)}</text>`,
  ]
  return [
    '<svg xmlns="http://www.w3.org/2000/svg" width="560" height="310" font-family="Arial, sans-serif">',
    '<rect width="560" height="310" fill="#fff" stroke="#111" stroke-width="2" stroke-dasharray="8 6"/>',
    ...lines,
    qr,
    '</svg>',
  ].join('\n')
}

export function ReturnLabelCard({ tracking }: { tracking: ReturnTracking }) {
  const qrRef = useRef<SVGSVGElement>(null)

  function download() {
    if (!qrRef.current) return
    const svg = buildLabelSvg(tracking, qrRef.current.outerHTML)
    const url = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `${tracking.rmaNumber}-return-label.svg`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <Card aria-labelledby="label-title">
      <h2 id="label-title" className="text-sm font-medium">
        Your return label
      </h2>
      <div className="mt-3 flex gap-4">
        <QRCodeSVG
          ref={qrRef}
          value={tracking.qrCode}
          size={88}
          marginSize={0}
          title={`QR code ${tracking.qrCode}`}
          xmlns="http://www.w3.org/2000/svg"
          className="shrink-0"
        />
        <div className="min-w-0 space-y-3">
          <p className="text-xs text-muted-foreground">
            Show this QR code at any drop-off point, or print the label and stick it on the parcel.
          </p>
          <Button variant="outline" size="sm" onClick={download}>
            Download label
          </Button>
        </div>
      </div>
    </Card>
  )
}

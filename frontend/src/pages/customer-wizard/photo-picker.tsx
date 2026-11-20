import { useEffect, useId, useState, type DragEvent, type Ref } from "react"
import { UploadIcon, XIcon } from "lucide-react"
import { MAX_PHOTO_BYTES, MAX_PHOTO_MB, MAX_PHOTOS, PHOTO_TYPES } from "@/api/returns"
import { cn } from "@/lib/utils"

const sameFile = (a: File, b: File) =>
  a.name === b.name && a.size === b.size && a.lastModified === b.lastModified

/** Splits new files into those that fit the photo rules and messages about the rest. */
function screenPhotos(current: File[], incoming: File[]) {
  const accepted: File[] = []
  const problems = new Set<string>()
  for (const file of incoming) {
    if ([...current, ...accepted].some((existing) => sameFile(existing, file))) continue
    if (!PHOTO_TYPES.includes(file.type)) problems.add(`${file.name} isn’t a JPG or PNG.`)
    else if (file.size > MAX_PHOTO_BYTES) problems.add(`${file.name} is larger than ${MAX_PHOTO_MB} MB.`)
    else if (current.length + accepted.length >= MAX_PHOTOS)
      problems.add(`You can add up to ${MAX_PHOTOS} photos.`)
    else accepted.push(file)
  }
  return { accepted, problems: [...problems] }
}

type PhotoPickerProps = {
  photos: File[]
  onPhotosChange: (photos: File[]) => void
  /** Message from the step, e.g. "Add at least one photo". */
  error?: string
  /** The file input, so the step can move focus here. */
  inputRef?: Ref<HTMLInputElement>
}

/**
 * Dropzone + thumbnails for item photos. Tap to take a photo or pick files,
 * or drop files on it. Enforces type, size and count limits.
 */
export function PhotoPicker({ photos, onPhotosChange, error, inputRef }: PhotoPickerProps) {
  const inputId = useId()
  const messageId = useId()
  const hintId = useId()
  const [problems, setProblems] = useState<string[]>([])
  const [dragging, setDragging] = useState(false)
  const full = photos.length >= MAX_PHOTOS
  const messages = [error, ...problems].filter((message) => message !== undefined)

  function addFiles(files: File[]) {
    const { accepted, problems } = screenPhotos(photos, files)
    setProblems(problems)
    if (accepted.length > 0) onPhotosChange([...photos, ...accepted])
  }

  function handleDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault()
    setDragging(false)
    if (!full) addFiles(Array.from(event.dataTransfer.files))
  }

  return (
    <div className="grid gap-3">
      <div>
        <input
          ref={inputRef}
          id={inputId}
          type="file"
          accept={PHOTO_TYPES.join(",")}
          multiple
          disabled={full}
          aria-invalid={error ? true : undefined}
          aria-describedby={messages.length > 0 ? `${messageId} ${hintId}` : hintId}
          className="peer sr-only"
          onChange={(event) => {
            addFiles(Array.from(event.target.files ?? []))
            // Let the same file be picked again after it's removed.
            event.target.value = ""
          }}
        />
        <label
          htmlFor={inputId}
          onDragOver={(event) => {
            event.preventDefault()
            if (!full) setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={handleDrop}
          className={cn(
            "grid cursor-pointer justify-items-center gap-1 rounded-md border border-dashed border-muted-foreground/40 bg-muted px-4 py-5 text-center transition-colors hover:border-muted-foreground/70 peer-focus-visible:ring-3 peer-focus-visible:ring-ring/50 peer-disabled:cursor-not-allowed peer-disabled:opacity-60 peer-disabled:hover:border-muted-foreground/40",
            dragging && "border-primary bg-background",
            error && "border-destructive",
          )}
        >
          <UploadIcon aria-hidden className="mb-1 size-5 text-muted-foreground" />
          <span className="text-[15px] font-semibold">
            {full ? `You’ve added ${MAX_PHOTOS} photos` : "Take a photo or upload"}
          </span>
          <span className="text-[13px] text-muted-foreground">
            JPG or PNG, up to {MAX_PHOTO_MB} MB each
          </span>
        </label>
      </div>

      {photos.length > 0 && (
        <ul aria-label="Added photos" className="flex flex-wrap gap-2">
          {photos.map((file, index) => (
            <PhotoThumb
              key={`${file.name}-${file.size}-${file.lastModified}`}
              file={file}
              onRemove={() => {
                setProblems([])
                onPhotosChange(photos.filter((_, i) => i !== index))
              }}
            />
          ))}
        </ul>
      )}

      {messages.length > 0 && (
        <div id={messageId} className="grid gap-0.5 text-sm text-destructive">
          {messages.map((message) => (
            <p key={message}>{message}</p>
          ))}
        </div>
      )}

      <p id={hintId} className="text-[13px] text-muted-foreground">
        Show the whole item and any damage or tags. Clear photos help us approve faster.
      </p>
    </div>
  )
}

/** Preview URL for a picked file, released when no longer shown. */
function useObjectUrl(file: File) {
  const [url, setUrl] = useState<string>()
  useEffect(() => {
    const objectUrl = URL.createObjectURL(file)
    setUrl(objectUrl)
    return () => URL.revokeObjectURL(objectUrl)
  }, [file])
  return url
}

function PhotoThumb({ file, onRemove }: { file: File; onRemove: () => void }) {
  const url = useObjectUrl(file)

  return (
    <li className="grid w-18 gap-1">
      <div className="relative size-18 overflow-hidden rounded-md bg-muted">
        {url && <img src={url} alt="" className="size-full object-cover" />}
        <button
          type="button"
          onClick={onRemove}
          aria-label={`Remove ${file.name}`}
          className="absolute top-1 right-1 grid size-6 cursor-pointer place-items-center rounded-full bg-background/90 text-foreground shadow-sm transition-colors outline-none hover:bg-background focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <XIcon aria-hidden className="size-3.5" />
        </button>
      </div>
      <span className="truncate text-xs text-muted-foreground" title={file.name}>
        {file.name}
      </span>
    </li>
  )
}

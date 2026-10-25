# 🎙️ Voice Recorder

A clean, single-file voice recorder that runs entirely in your browser. No build step, no backend, no uploads — your audio never leaves your device.

**[▶ Open the app](https://github.com/)** — replace this link with your GitHub Pages URL once enabled.

## Features

- **One-tap recording** using the native `MediaRecorder` API
- **Live waveform** visualiser driven by the Web Audio `AnalyserNode`
- **Pause / resume** mid-recording
- **Mute** the mic without stopping the take
- **Playback** right in the list, with a progress bar
- **Download** clips as `.webm` / `.m4a` (browser dependent)
- **Persistent storage** — recordings survive a page reload (IndexedDB)
- **Fully responsive** dark UI, tuned for mobile and desktop
- **Zero dependencies** — one `index.html` file, ~25 KB

## Run it

### Option 1 — GitHub Pages (recommended)

1. Go to **Settings → Pages** in this repo
2. Under *Source*, pick **Deploy from a branch**
3. Branch: `main`, folder: `/ (root)` → **Save**
4. Your app goes live at `https://<username>.github.io/<repo-name>/`

### Option 2 — locally

```bash
git clone https://github.com/<username>/voice-recorder.git
cd voice-recorder
python3 -m http.server 8000
```

Then open <http://localhost:8000>.

> **Note:** opening `index.html` directly via `file://` will not work. Browsers block microphone access on insecure origins — you need `https://` or `localhost`.

## Browser support

| Browser | Record | Playback |
|---|---|---|
| Chrome / Edge (desktop + Android) | ✅ webm/opus | ✅ |
| Firefox | ✅ webm/opus | ✅ |
| Safari (macOS 14.1+, iOS 14.3+) | ✅ mp4/aac | ✅ |

## How it works

1. `getUserMedia()` asks for microphone permission and returns a `MediaStream`.
2. The stream is piped into an `AnalyserNode` to draw the live waveform on a `<canvas>`.
3. A `MediaRecorder` collects the stream in 250 ms chunks.
4. On stop, the chunks are packed into a `Blob`, stored in IndexedDB, and rendered in the list with an object URL for playback.

## Privacy

Everything stays on your machine. There is no server, no analytics, no network request after the page loads. Recordings live in your browser's IndexedDB until you delete them — and clearing site data wipes them for good.

## License

MIT — see [LICENSE](LICENSE).

# MP4 loop preparation

Source: `../grok_video_2026-08-16-18-48-37.mp4`

## Audit result

- 560×560, H.264 High, 24 fps, 145 frames, 6.041667 s.
- No audio stream. The source also contains an attached MJPEG poster stream; processed variants keep only the primary video stream.
- First and last source frames are not identical. The direct loop therefore has a visible endpoint discontinuity; endpoint PSNR after normalization: 28.44 dB.

## Prepared variants

| File | Duration | Intended use | Endpoint check |
|---|---:|---|---:|
| `era_loop_direct.mp4` | 6.041667 s | Baseline, preserves the original timing | PSNR 28.44 dB; seam remains |
| `era_loop_pingpong.mp4` | 12.083333 s | Seam-free visual cycle when reverse playback is acceptable | PSNR 36.83 dB |
| `era_loop_crossfade.mp4` | 6.041667 s | Cyclic loop with a 0.5 s blended transition | PSNR 28.80 dB; blend softens but does not eliminate motion change |

All variants are 560×560, H.264/yuv420p, 24 fps, video-only MP4 with `faststart` metadata. No application or production source files were changed.

## Recommendation

Use `era_loop_pingpong.mp4` when a clean visible cycle is the priority. Use `era_loop_crossfade.mp4` when the fixed ~6 s duration matters. Keep `era_loop_direct.mp4` only as the unmodified-timing baseline.

## Verification

- `ffprobe`: stream dimensions, codec, frame rate, frame count and duration checked.
- Endpoint frames extracted and compared for each variant.
- Output files were generated successfully from the source; device/player human review is still required for final visual acceptance.

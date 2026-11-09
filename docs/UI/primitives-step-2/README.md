# Step 2 shared-primitives gallery

Raw emulator captures of `OpenRunComponentsGallery()`, the same composable used by the six Gallery `@Preview` annotations. Width 390 dp, density 320, Android 17/API 37. All values are explicitly labeled preview fixtures; no app or health data is loaded.

Captures are paginated by vertical scrolling because the emulator caps the display at 7200 px. The last page is clamped to the end and overlaps the first page; PNGs are unedited. Large-font charts scroll horizontally to retain readable labels and proportional geometry; these images show their initial horizontal position. The final gallery label and text-line bounds were checked in all configurations.

| Theme | Font scale | Captures |
|---|---|---|
| Light | 100% | [Page 1](gallery-light-100-page-1.png) |
| Light | 130% | [Page 1](gallery-light-130-page-1.png), [Page 2](gallery-light-130-page-2.png) |
| Light | 200% | [Page 1](gallery-light-200-page-1.png), [Page 2](gallery-light-200-page-2.png) |
| Dark | 100% | [Page 1](gallery-dark-100-page-1.png) |
| Dark | 130% | [Page 1](gallery-dark-130-page-1.png), [Page 2](gallery-dark-130-page-2.png) |
| Dark | 200% | [Page 1](gallery-dark-200-page-1.png), [Page 2](gallery-dark-200-page-2.png) |

Compared with Today, Plan, Runs, Run detail and checkout reference boards in `../redesign/`. The approved resolution for split widths is documented in spec §3.11: fastest = 100%, others = speed / fastest speed. PNG bar lengths are illustrative.

See [verification details](../../primitives-step-2-verification.md). These are shared-component previews, not redesigned production-screen screenshots.

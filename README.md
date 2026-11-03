# Android Audio Book

A free, private, account-free PDF-to-speech Android app.

## First milestone
- Import text-based PDFs with Android's document picker.
- Extract PDF text locally using PDFBox-Android.
- Read aloud using Android's installed TextToSpeech engine, without paid AI services.
- Play/pause, skip between text sections, adjust reading speed (0.5–2.0×).
- Save the last section per PDF locally.

**Current limitations:** Scanned PDFs need OCR (not yet implemented). Speech plays while the activity is alive; true background playback, chapter detection, audiobook library, voice selection and audio-file export are planned next. Pause resumes at the beginning of the current section. Some offline voices require a one-time installation through device settings.

## Build
Use Android Studio with JDK 17 and Android SDK 36, or run:

```powershell
gradle --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds an APK artifact.

## Privacy
No account, ads, cloud narration or app network permission required for audiobook features. The starter's unused Cloudflare/Google modules remain in the repository but are not part of the app workflow.

## License
MIT — Copyright (c) 2026 Joseph Niksa. See [LICENSE](LICENSE). Third-party components retain their own licenses; see [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES).

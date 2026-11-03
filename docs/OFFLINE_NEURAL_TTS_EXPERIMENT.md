# Offline neural TTS experiment

This branch is an isolated testbed. The production reader remains on `main`.

## Objective
Improve long-form audiobook narration while retaining **free, fully offline playback**, no account, no metered APIs, and no mandatory cloud service.

## Candidates
1. **External Android neural TTS engine** (fastest baseline): HayaiTTS / Kokoly / NekoTTS. Install separately and select as system engine. Confirm our current Android TextToSpeech integration works, especially range callbacks and highlighting.
2. **Piper/VITS via sherpa-onnx**: prioritize speed, RAM and model size.
3. **Kokoro-82M via sherpa-onnx**: prioritize voice naturalness and listening comfort.

## Acceptance tests
- A/B compare identical passages (narrative, dialogue, numbers, abbreviations).
- Offline test in airplane mode, including reopening and playback.
- Measure first-audio latency, sustained synthesis time, battery, memory, APK/model size.
- Test speech interruption, chapter jumps, voice preview, speed control and pause/resume.
- Confirm speech-range callbacks: if neural engine does not provide them, preserve PDF highlighting through an alternate timing/alignment mechanism.
- Verify runtime, phonemizer and **each model/voice** redistribution license before bundling.
- Never place proprietary models, credentials, or large binaries into git.

## First experiment
Install HayaiTTS from its upstream release and a neural voice, set it as Android's preferred TTS engine, then test our existing APK. The app's voice picker may need to expose the engine selector or open Android's speech settings. Record behavior before changing the production reader.

References:
- https://github.com/HayaiApp/HayaiTTS
- https://github.com/k2-fsa/sherpa-onnx
- https://github.com/loom7/kokoly
- https://github.com/siva-sub/NekoTTS

**Not implemented yet:** in-app ONNX inference or model downloads. This branch is currently a research and test plan.

## Website evaluation: voice-generator.com (2026-11-03)

- Homepage: https://voice-generator.com/
- Advertises free unlimited generation, MP3 export, no signup, and named voices (Adam, Puck, Liam, Heart, Bella, Jessica, Echo, Eric, Fenrir, Michael, Onyx, Santa, Alloy, Aoede, Kore, Nicole, Nova, River, Sarah).
- Terms (https://voice-generator.com/terms) state: “Voices powered by Kokoro under the Apache 2.0 License.”
- **Do not assume** the website's source code, API, voice branding, or every voice file is open source. The model's license does not automatically license the website or other assets.
- No supported public integration API was confirmed. Do not scrape website generation endpoints or embed a dependency on the website.
- Preferred independent implementation: run a licensed Kokoro ONNX model locally on Android, evaluate sherpa-onnx Android runtime, and keep the existing Android TTS engine as a fallback.
- Verify exact model weights, voice pack, phonemizer, dependencies, and Android redistribution rights before committing binaries or packaging releases.

### Proposed architecture

1. Add a `NarrationEngine` abstraction with `speak(text)`, `stop()`, `setSpeed()`, `selectVoice()`, and playback events.
2. Retain `AndroidTtsEngine` as fallback and baseline.
3. Prototype `KokoroOnnxEngine` on this branch only; download/install model assets separately from the APK where licensing permits.
4. Synthesize short sections on-device, cache audio locally, and play through Android Media3.
5. Measure alignment drift: synthesized audio does not inherently provide Android `onRangeStart` callbacks. Implement word timestamps or alignment before claiming accurate PDF word highlighting.
6. Keep airplane-mode functionality, model download size, thermal behavior, and battery usage as acceptance gates.

### Initial decision

Prioritize **Kokoro ONNX / sherpa-onnx** for natural voice quality and **Piper** for lower-end device performance. No website API integration planned.

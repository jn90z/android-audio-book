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

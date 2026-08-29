# Wake word trainer

Trains the on-device model behind Kate's wake phrases — "Hey Kate", "Hi
Kate", "Hello Kate", "What's up Kate" — treated as a single binary "wake vs.
not" classifier (`com.dti.kate.wakeword.WakeWordDetector`). Runs in Google
Colab - no local GPU needed.
 
## Why this exists

Android gives no way to keep a full speech-to-text model (Vosk) running
continuously in the background without killing the battery - that's what
the mic timeout in Settings is for on the command-listening side. A wake
word needs something much lighter: a small binary classifier that only
answers "was that 'Hey Kate' or not," running every ~200ms on a 1-second
rolling window of audio. That's what this trains.

## Steps

1. Collect audio (see `train_wake_word.py`'s module docstring for exactly
   what and how much - short version: 200+ clips per phrase across all
   four accepted phrases, all in the same `positive/` folder since this is
   a single binary class; 600+ negative clips including near-misses like
   partial phrases and other names).
2. Lay it out as:
   ```
   data/positive/*.wav     (16kHz mono "Hey Kate")
   data/negative/*.wav     (16kHz mono, anything else)
   data/background/*.wav   (ambient noise, for augmentation only)
   ```
3. Open Colab, paste in `train_wake_word.py`, point `DATA_DIR` at your
   uploaded `data/` folder (Google Drive mount is easiest for a few hundred
   WAVs), run it.
4. Output is `wakeword.tflite`. Copy it to
   `app/src/main/assets/wakeword.tflite` and rebuild.

Without that file present, `WakeWordDetector.initialize()` returns false and
the feature just silently stays off (tap/raise/shake still work) - so this
can be dropped in whenever it's ready, no code changes needed on that side.

## If accuracy is bad

Almost always a data issue, not a model issue - see the WARNING the script
prints if validation accuracy comes in under 95%. In rough order of impact:

1. Not enough positive clips, or all from one voice/session.
2. Negatives don't include phonetically similar words ("Kate" alone, "OK",
   names that sound similar) - these near-misses are what actually teaches
   the boundary.
3. No background-noise augmentation variety - the `data/background/` clips
   feed directly into how robust the model is to a noisy room.

## Re-training

Nothing here is architecture-locked to exactly this model - if you want a
bigger/smaller model, more classes (e.g. separate "unknown speech" vs
"silence" instead of merging both into the negative class), or a different
window size, that's a `train_wake_word.py` change. Just remember the
feature-extraction constants at the top of both `train_wake_word.py` and
`MelSpectrogram.kt` have to match exactly - see the contract comment in
each file.

"""
Kate wake-word trainer - run this in Google Colab. Accepts any of four
phrases as a single "wake" class: "Hey Kate", "Hi Kate", "Hello Kate",
"What's up Kate" - see DATA YOU NEED TO COLLECT below for how to split
that across data/positive/.

USAGE IN COLAB
  1. Runtime > Change runtime type > GPU (not required but faster).
  2. Paste this whole file into a cell, or upload it and `%run train_wake_word.py`.
  3. Fill in the DATA COLLECTION section below with your own recordings.
  4. Run all cells. Output: wakeword.tflite
  5. Copy wakeword.tflite into app/src/main/assets/wakeword.tflite in the
     Android project and rebuild - WakeWordDetector.kt picks it up
     automatically (feature silently stays off without it, so this is a
     safe drop-in).

FEATURE CONTRACT - READ BEFORE CHANGING ANYTHING
  The Kotlin side (app/src/main/java/com/dti/kate/wakeword/MelSpectrogram.kt)
  computes features with these exact parameters. If you change any constant
  in extract_features() below, you MUST change the matching constant in
  MelSpectrogram.kt and retrain - a mismatch won't error, it'll just silently
  perform much worse on-device than in this notebook.

    sample_rate   = 16000 Hz
    window        = 480 samples (30ms)
    hop           = 320 samples (20ms)
    fft_size      = 512
    mel_bins      = 40
    input_seconds = 1.0s (16000 samples) -> 49 frames x 40 mel bins

DATA YOU NEED TO COLLECT
  positive/  - ~200+ clips per phrase (800+ total) covering all four wake
               phrases Kate accepts: "Hey Kate", "Hi Kate", "Hello Kate",
               "What's up Kate". All four go in the SAME positive/ folder -
               this is a binary classifier (wake-word vs. not), not one
               class per phrase, since the app reacts identically no matter
               which phrase was said. Skewing heavily toward one phrase and
               light on the others will make the model much better at
               detecting that one phrase than the rest, so keep counts
               roughly even across all four. Vary within each phrase too:
                 - your own voice at different times of day / energy levels
                 - if possible, other people's voices (accuracy on voices
                   unlike the training set is the #1 real-world failure mode)
                 - phone in hand vs. on a table vs. in a pocket
                 - quiet room vs. TV/traffic/kitchen noise in the background
  negative/  - ~600+ clips of NOT any wake phrase: other speech (read a book
               out loud, other commands like "turn on the flashlight"),
               music, TV, silence, room noise, other people's names. With
               four accepted phrases sharing "Kate" and overlapping lead-ins
               ("hi"/"hello"/"hey"), deliberately include near-misses that
               only partially match: "Kate" alone, "hey" alone, "hi" alone,
               "hello" alone, "what's up" alone (no Kate), other names in
               place of Kate ("Hey Nate", "Hi Kevin"), and greetings aimed
               at a person, not the phone. These near-miss negatives are
               what actually teaches the model the boundary - undifferentiated
               negatives alone won't.
  background/ - long (1-5 min) ambient noise/music recordings, used only
                for augmentation (mixed under both classes below), not
                classified directly.

  All clips: mono WAV, 16kHz (or anything ffmpeg/librosa can resample from -
  resampling is handled below). Positive/negative clips don't need to be
  exactly 1s - shorter ones are auto-padded, longer ones get a random 1s
  crop per epoch (see WakeWordDataset).

  Folder layout expected below:
    data/positive/*.wav
    data/negative/*.wav
    data/background/*.wav
"""

import glob
import os
import random

import numpy as np
import tensorflow as tf

# ============================================================
# Feature extraction - MUST MATCH MelSpectrogram.kt EXACTLY
# ============================================================
SAMPLE_RATE = 16000
WINDOW_SIZE = 480      # 30ms
HOP_SIZE = 320         # 20ms
FFT_SIZE = 512
MEL_BINS = 40
INPUT_SAMPLES = SAMPLE_RATE  # 1.0s
NUM_FRAMES = (INPUT_SAMPLES - WINDOW_SIZE) // HOP_SIZE + 1  # 49


def extract_features(samples: np.ndarray) -> np.ndarray:
    """samples: float32 array of exactly INPUT_SAMPLES, range [-1, 1]. Returns (NUM_FRAMES, MEL_BINS) log-mel spectrogram - same math as MelSpectrogram.kt's extract(), via tf.signal so it's differentiable-graph-friendly and matches a Hann window + HTK mel scale."""
    assert len(samples) == INPUT_SAMPLES, f"expected {INPUT_SAMPLES} samples, got {len(samples)}"

    stfts = tf.signal.stft(
        samples,
        frame_length=WINDOW_SIZE,
        frame_step=HOP_SIZE,
        fft_length=FFT_SIZE,
        # periodic=False -> symmetric Hann (denominator N-1), matching
        # MelSpectrogram.kt's hand-rolled window exactly. TF's default
        # (periodic=True, denominator N) is a different window and WILL
        # cause a train/inference mismatch if left as the default here.
        window_fn=lambda length, dtype: tf.signal.hann_window(length, periodic=False, dtype=dtype),
    )
    power = tf.math.real(stfts) ** 2 + tf.math.imag(stfts) ** 2  # (NUM_FRAMES, FFT_SIZE/2+1)

    num_spectrogram_bins = power.shape[-1]
    mel_weight_matrix = tf.signal.linear_to_mel_weight_matrix(
        num_mel_bins=MEL_BINS,
        num_spectrogram_bins=num_spectrogram_bins,
        sample_rate=SAMPLE_RATE,
        lower_edge_hertz=20.0,
        upper_edge_hertz=SAMPLE_RATE / 2.0,
    )
    mel = tf.tensordot(power, mel_weight_matrix, 1)
    log_mel = tf.math.log(tf.maximum(mel, 1e-10))
    return log_mel.numpy()  # (NUM_FRAMES, MEL_BINS)


# ============================================================
# Data loading + augmentation
# ============================================================
def load_wav_16k(path: str) -> np.ndarray:
    audio_binary = tf.io.read_file(path)
    audio, sr = tf.audio.decode_wav(audio_binary, desired_channels=1)
    audio = tf.squeeze(audio, axis=-1)
    if sr != SAMPLE_RATE:
        # tf.audio.decode_wav doesn't resample - if your clips aren't
        # already 16kHz, resample them up front, e.g.:
        #   ffmpeg -i in.wav -ar 16000 -ac 1 out.wav
        raise ValueError(f"{path}: expected {SAMPLE_RATE}Hz, got {sr}Hz - resample first (see comment)")
    return audio.numpy()


def fit_to_length(samples: np.ndarray, background_pool: list, augment: bool) -> np.ndarray:
    """Pads/crops to exactly INPUT_SAMPLES. Random crop position and optional background-noise mixing when augment=True, so the model sees the wake word at different positions/energy levels within the window rather than always centered and clean."""
    n = len(samples)
    if n < INPUT_SAMPLES:
        pad_total = INPUT_SAMPLES - n
        pad_left = random.randint(0, pad_total) if augment else pad_total // 2
        samples = np.pad(samples, (pad_left, pad_total - pad_left))
    elif n > INPUT_SAMPLES:
        start = random.randint(0, n - INPUT_SAMPLES) if augment else (n - INPUT_SAMPLES) // 2
        samples = samples[start:start + INPUT_SAMPLES]

    if augment and background_pool and random.random() < 0.7:
        bg = random.choice(background_pool)
        bg_start = random.randint(0, max(1, len(bg) - INPUT_SAMPLES))
        bg_clip = bg[bg_start:bg_start + INPUT_SAMPLES]
        if len(bg_clip) < INPUT_SAMPLES:
            bg_clip = np.pad(bg_clip, (0, INPUT_SAMPLES - len(bg_clip)))
        mix_level = random.uniform(0.05, 0.35)  # keep speech dominant
        samples = samples * (1 - mix_level) + bg_clip * mix_level

    if augment:
        samples = samples * random.uniform(0.7, 1.3)  # random gain, mirrors on-device level variance

    return np.clip(samples, -1.0, 1.0).astype(np.float32)


def build_dataset(data_dir: str, augment_multiplier: int = 1):
    """augment_multiplier: how many augmented copies to generate per source clip. Use >1 for the positive class if you have far fewer positive than negative clips (common - "Hey Kate" recordings are more effort to collect than negatives)."""
    positives = sorted(glob.glob(os.path.join(data_dir, "positive", "*.wav")))
    negatives = sorted(glob.glob(os.path.join(data_dir, "negative", "*.wav")))
    background = sorted(glob.glob(os.path.join(data_dir, "background", "*.wav")))

    assert positives, f"No files in {data_dir}/positive/ - see this script's module docstring for what to collect"
    assert negatives, f"No files in {data_dir}/negative/ - see this script's module docstring for what to collect"

    background_pool = [load_wav_16k(p) for p in background]

    features, labels = [], []
    for path in positives:
        raw = load_wav_16k(path)
        for _ in range(augment_multiplier):
            fitted = fit_to_length(raw, background_pool, augment=True)
            features.append(extract_features(fitted))
            labels.append(1)
    for path in negatives:
        raw = load_wav_16k(path)
        fitted = fit_to_length(raw, background_pool, augment=True)
        features.append(extract_features(fitted))
        labels.append(0)

    X = np.stack(features)[..., np.newaxis]  # (N, NUM_FRAMES, MEL_BINS, 1)
    y = np.array(labels, dtype=np.int32)
    return X, y


# ============================================================
# Model - small depthwise-separable CNN (DS-CNN), the standard
# architecture for on-device keyword spotting (see Google's "Hello Edge"
# KWS benchmark) - accurate enough for a single keyword at a size that
# runs comfortably in a 200ms inference budget on a low-end phone.
# ============================================================
def build_model() -> tf.keras.Model:
    inputs = tf.keras.Input(shape=(NUM_FRAMES, MEL_BINS, 1))
    x = tf.keras.layers.Conv2D(8, 3, strides=(2, 2), padding="same", activation="relu")(inputs)
    x = tf.keras.layers.BatchNormalization()(x)

    for filters in (16, 32, 32):
        x = tf.keras.layers.DepthwiseConv2D(3, padding="same", activation="relu")(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.Conv2D(filters, 1, activation="relu")(x)
        x = tf.keras.layers.BatchNormalization()(x)

    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.3)(x)
    outputs = tf.keras.layers.Dense(2, activation="softmax")(x)
    return tf.keras.Model(inputs, outputs)


# ============================================================
# Train + export
# ============================================================
def main():
    DATA_DIR = "data"  # expects data/positive, data/negative, data/background

    print("Loading and featurizing data...")
    # If positives are scarce relative to negatives, raise augment_multiplier
    # (e.g. 5-8x) to balance classes rather than letting the model learn
    # "predict negative" as a cheap shortcut.
    X, y = build_dataset(DATA_DIR, augment_multiplier=6)
    print(f"positives={int(y.sum())} negatives={int((y == 0).sum())} total={len(y)}")

    idx = np.random.permutation(len(y))
    X, y = X[idx], y[idx]
    split = int(len(y) * 0.85)
    X_train, y_train = X[:split], y[:split]
    X_val, y_val = X[split:], y[split:]

    model = build_model()
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    model.summary()

    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=40,
        batch_size=32,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(patience=6, restore_best_weights=True, monitor="val_accuracy"),
            tf.keras.callbacks.ReduceLROnPlateau(patience=3, factor=0.5),
        ],
    )

    val_loss, val_acc = model.evaluate(X_val, y_val)
    print(f"Final validation accuracy: {val_acc:.4f}")
    if val_acc < 0.95:
        print(
            "WARNING: val accuracy under 95% - almost always a data problem, not a "
            "model problem. Most common causes: too few positive clips, negatives "
            "that don't include near-miss/phonetically-similar words, or all "
            "positives recorded in one sitting/voice (collect more variety)."
        )

    # --- Convert to TFLite with int8 quantization ---
    # Full-integer quantization roughly quarters the model size and speeds
    # up inference on-device with a small accuracy cost - worth it for a
    # model that runs continuously in the background. Requires a
    # representative dataset (a sample of real input distributions) for TF
    # to calibrate activation ranges.
    def representative_dataset():
        for i in range(min(200, len(X_train))):
            yield [X_train[i:i + 1].astype(np.float32)]

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.float32  # keep float32 I/O - WakeWordDetector.kt feeds float32 features directly, only weights/activations are int8 internally
    converter.inference_output_type = tf.float32

    tflite_model = converter.convert()
    with open("wakeword.tflite", "wb") as f:
        f.write(tflite_model)

    size_kb = len(tflite_model) / 1024
    print(f"Wrote wakeword.tflite ({size_kb:.0f} KB)")
    print("Copy this file to app/src/main/assets/wakeword.tflite in the Android project.")


if __name__ == "__main__":
    main()

# Piper TTS voice (piper-plus)

Backs `PiperTtsEngine.kt` / `KateTtsEngine.kt`. Same opt-in pattern as the
wake word and Vosk model - if `assets/piper_voice.onnx` and
`assets/piper_voice.onnx.json` aren't present, `KateTtsEngine` silently
falls back to Android's built-in `TextToSpeech`. Nothing breaks either way;
this just gets you a better voice once it exists.

## Why piper-plus and not "real" Piper

The Piper ecosystem forked in late 2025:

| Repo | License | Status |
|---|---|---|
| `rhasspy/piper` / `piper-phonemize` | - | **Archived Oct 2025**, unmaintained |
| `OHF-Voice/piper1-gpl` (official successor) | **GPL-3.0** | Active, but GPL linked into a closed-source app is real copyleft exposure - get that reviewed by counsel before using it, don't just wire it in |
| `ayutaz/piper-plus` | **MIT** | Active, explicitly built "espeak-ng free" for commercial/embedded use, ships a real Android AAR on Maven Central |

We're using piper-plus for the clean MIT licensing. The trade-off: it uses
its own G2P (text→phoneme) implementation instead of espeak-ng, so it is
**not** phoneme-compatible with the hundreds of existing community Piper
voices on Hugging Face (those were trained against espeak-ng's phoneme set).
You need a voice actually trained through piper-plus's own pipeline - either
train one yourself or use one someone else trained with piper-plus
specifically.

## What you need

Two files, either from the pretrained base model or your own fine-tune (see
below), placed at:
```
app/src/main/assets/piper_voice.onnx
app/src/main/assets/piper_voice.onnx.json
```

### Path A - fastest, generic voice (no training)

piper-plus publishes a pretrained multilingual base checkpoint - English is
one of its trained languages, so this works without any fine-tuning:

1. `pip install piper-plus`
2. Download the checkpoint from Hugging Face: `ayousanz/piper-plus-base`
3. Export to ONNX:
   ```
   CUDA_VISIBLE_DEVICES="" uv run python -m piper_train.export_onnx \
     /path/to/checkpoint.ckpt /path/to/piper_voice.onnx
   ```
4. Grab the matching `config.json` from the same HF repo, rename to
   `piper_voice.onnx.json`
5. Drop both into `app/src/main/assets/`

Good for validating the integration actually works end-to-end. Voice will
be the shared base voice, not a distinctive "Kate."

### Path B - a real Kate voice (fine-tuned)

1. Record a single-speaker dataset reading a varied script - same spirit as
   the wake word data collection, but here you want a large amount (Piper-
   style fine-tunes typically want 30+ minutes of clean audio) from ONE
   consistent voice, not many voices.
2. Fine-tune from `ayousanz/piper-plus-base` on that dataset - see
   piper-plus's own Training Guide (github.com/ayutaz/piper-plus) for the
   fine-tuning flow.
3. Gotcha from their own model card: after a single-speaker fine-tune,
   before ONNX export, copy the trained language embedding to all language
   slots or export can behave oddly:
   ```python
   import torch
   ckpt = torch.load("checkpoint.ckpt", map_location="cpu")
   state = ckpt["state_dict"]
   emb_lang = state["model_g.emb_lang.weight"]
   for i in range(1, emb_lang.shape[0]):
       emb_lang[i] = emb_lang[0].clone()
   torch.save(ckpt, "checkpoint-emb_lang_fixed.ckpt")
   ```
4. Export to ONNX the same way as Path A, same drop-in location.

## Before wiring this into a real build

`PiperTtsEngine.kt`'s ONNX input/output tensor names (`input`,
`input_lengths`, `scales` in; `output` out) and the phoneme-id interleaving
logic (BOS/pad/EOS) are based on the *standard* Piper VITS export format,
confirmed against Piper's own docs - not verified against an actual
piper-plus-exported voice, since none exists yet. First thing to check once
you have a real `piper_voice.onnx.json`: does its `phoneme_id_map` actually
contain `"^"`/`"$"`/`"_"` entries matching that convention. If piper-plus's
export differs, `phonemesToIds()` in `PiperTtsEngine.kt` is the one place to
adjust.

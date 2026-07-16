// app/src/main/cpp/third_party/vosk/include/vosk_api.h

#ifndef VOSK_API_H
#define VOSK_API_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stddef.h>

// Vosk model handle
typedef struct VoskModel VoskModel;

// Vosk recognizer handle
typedef struct VoskRecognizer VoskRecognizer;

// Vosk batch recognizer handle
typedef struct VoskBatchRecognizer VoskBatchRecognizer;

// Vosk speaker model handle
typedef struct VoskSpeakerModel VoskSpeakerModel;

// Vosk spk model handle
typedef struct VoskSpkModel VoskSpkModel;

// ==================== Model ====================

/**
 * Load a Vosk model from a directory
 * @param model_path Path to the model directory
 * @return VoskModel handle or NULL on error
 */
VoskModel* vosk_model_new(const char* model_path);

/**
 * Free a Vosk model
 * @param model VoskModel handle
 */
void vosk_model_free(VoskModel* model);

/**
 * Check if model is ready
 * @param model VoskModel handle
 * @return 1 if ready, 0 otherwise
 */
int vosk_model_ready(VoskModel* model);

/**
 * Get word list from model
 * @param model VoskModel handle
 * @return JSON string with word list
 */
const char* vosk_model_find_word(VoskModel* model, const char* word);

// ==================== Recognizer ====================

/**
 * Create a new Vosk recognizer
 * @param model VoskModel handle
 * @param sample_rate Audio sample rate (16000 recommended)
 * @return VoskRecognizer handle or NULL on error
 */
VoskRecognizer* vosk_recognizer_new(VoskModel* model, float sample_rate);

/**
 * Create a new Vosk recognizer with custom grammar
 * @param model VoskModel handle
 * @param sample_rate Audio sample rate
 * @param grammar JSON string with grammar definition
 * @return VoskRecognizer handle or NULL on error
 */
VoskRecognizer* vosk_recognizer_new_grm(VoskModel* model, float sample_rate, const char* grammar);

/**
 * Create a new Vosk recognizer with speaker identification
 * @param model VoskModel handle
 * @param sample_rate Audio sample rate
 * @param spk_model VoskSpkModel handle
 * @return VoskRecognizer handle or NULL on error
 */
VoskRecognizer* vosk_recognizer_new_spk(VoskModel* model, float sample_rate, VoskSpkModel* spk_model);

/**
 * Free a Vosk recognizer
 * @param recognizer VoskRecognizer handle
 */
void vosk_recognizer_free(VoskRecognizer* recognizer);

/**
 * Reset recognizer state (clear partial and final results)
 * @param recognizer VoskRecognizer handle
 */
void vosk_recognizer_reset(VoskRecognizer* recognizer);

/**
 * Feed audio data to recognizer
 * @param recognizer VoskRecognizer handle
 * @param data PCM audio data (int16_t)
 * @param length Number of samples (not bytes!)
 * @return 1 if final result is available, 0 otherwise
 */
int vosk_recognizer_accept_waveform(VoskRecognizer* recognizer, const int16_t* data, size_t length);

/**
 * Get partial result (interim transcription)
 * @param recognizer VoskRecognizer handle
 * @return JSON string with partial result
 */
const char* vosk_recognizer_partial_result(VoskRecognizer* recognizer);

/**
 * Get final result
 * @param recognizer VoskRecognizer handle
 * @return JSON string with final result
 */
const char* vosk_recognizer_result(VoskRecognizer* recognizer);

/**
 * Get final result with alternatives
 * @param recognizer VoskRecognizer handle
 * @param alternatives Number of alternatives to return
 * @return JSON string with final result and alternatives
 */
const char* vosk_recognizer_result_with_alternatives(VoskRecognizer* recognizer, int alternatives);

/**
 * Set max alternatives to return
 * @param recognizer VoskRecognizer handle
 * @param max_alternatives Maximum number of alternatives
 */
void vosk_recognizer_set_max_alternatives(VoskRecognizer* recognizer, int max_alternatives);

/**
 * Set word confidence to return
 * @param recognizer VoskRecognizer handle
 * @param enable 1 to enable, 0 to disable
 */
void vosk_recognizer_set_word_confidence(VoskRecognizer* recognizer, int enable);

// ==================== Batch Recognizer ====================

/**
 * Create a batch recognizer for streaming
 * @param model VoskModel handle
 * @param sample_rate Audio sample rate
 * @return VoskBatchRecognizer handle or NULL on error
 */
VoskBatchRecognizer* vosk_batch_recognizer_new(VoskModel* model, float sample_rate);

/**
 * Free batch recognizer
 * @param batch_recognizer VoskBatchRecognizer handle
 */
void vosk_batch_recognizer_free(VoskBatchRecognizer* batch_recognizer);

/**
 * Accept waveform for batch processing
 * @param batch_recognizer VoskBatchRecognizer handle
 * @param data PCM audio data
 * @param length Number of samples
 */
void vosk_batch_recognizer_accept_waveform(VoskBatchRecognizer* batch_recognizer, const int16_t* data, size_t length);

/**
 * Get batch result
 * @param batch_recognizer VoskBatchRecognizer handle
 * @return JSON string with results
 */
const char* vosk_batch_recognizer_result(VoskBatchRecognizer* batch_recognizer);

// ==================== Speaker Model ====================

/**
 * Load speaker model
 * @param model_path Path to speaker model directory
 * @return VoskSpkModel handle or NULL on error
 */
VoskSpkModel* vosk_spk_model_new(const char* model_path);

/**
 * Free speaker model
 * @param spk_model VoskSpkModel handle
 */
void vosk_spk_model_free(VoskSpkModel* spk_model);

// ==================== Logging ====================

/**
 * Set Vosk log level
 * @param level 0 = no logs, 1 = errors, 2 = warnings, 3 = info
 */
void vosk_set_log_level(int level);

#ifdef __cplusplus
}
#endif

#endif // VOSK_API_H

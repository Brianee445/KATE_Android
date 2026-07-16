// app/src/main/cpp/intent/intent_classifier.cpp

#include "intent_classifier.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>
#include <sstream>
#include <fstream>

#define LOG_TAG "IntentClassifier"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace kate {

IntentClassifier::IntentClassifier() {
    buildVocabulary();
}

IntentClassifier::~IntentClassifier() {
    unloadModel();
}

bool IntentClassifier::loadModel(const std::string& modelPath) {
    if (m_loaded) {
        unloadModel();
    }
    
    try {
        // Load TFLite model
        m_model = tflite::FlatBufferModel::BuildFromFile(modelPath.c_str());
        if (!m_model) {
            LOGE("Failed to load TFLite model from: %s", modelPath.c_str());
            return false;
        }
        
        // Build interpreter
        tflite::InterpreterBuilder(*m_model, m_resolver)(&m_interpreter);
        if (!m_interpreter) {
            LOGE("Failed to build TFLite interpreter");
            return false;
        }
        
        // Allocate tensors
        if (m_interpreter->AllocateTensors() != kTfLiteOk) {
            LOGE("Failed to allocate tensors");
            return false;
        }
        
        // Check input/output dimensions
        int inputCount = m_interpreter->inputs().size();
        int outputCount = m_interpreter->outputs().size();
        
        if (inputCount == 0 || outputCount == 0) {
            LOGE("Invalid model: no inputs or outputs");
            return false;
        }
        
        // Get output size (number of intents)
        TfLiteTensor* output = m_interpreter->output_tensor(0);
        if (output->dims->size >= 2) {
            int intentCount = output->dims->data[1];
            m_intents.resize(intentCount);
            for (int i = 0; i < intentCount; i++) {
                m_intents[i] = "intent_" + std::to_string(i);
            }
        }
        
        m_loaded = true;
        LOGI("Intent classifier loaded successfully");
        return true;
        
    } catch (const std::exception& e) {
        LOGE("Exception loading model: %s", e.what());
        return false;
    }
}

void IntentClassifier::unloadModel() {
    if (m_interpreter) {
        m_interpreter.reset();
    }
    if (m_model) {
        m_model.reset();
    }
    m_loaded = false;
    LOGI("Intent classifier unloaded");
}

ClassifierResult IntentClassifier::classify(const std::string& text) {
    ClassifierResult result;
    result.intent = "unknown";
    result.confidence = 0.0f;
    
    if (!m_loaded || !m_interpreter) {
        return result;
    }
    
    try {
        // Preprocess text
        std::string processed = preprocessText(text);
        std::vector<float> tokens = tokenize(processed);
        
        // Get input tensor
        TfLiteTensor* input = m_interpreter->input_tensor(0);
        if (!input) {
            LOGE("Failed to get input tensor");
            return result;
        }
        
        // Copy tokens to input tensor
        // Assuming input is [1, seq_len] with float values
        float* inputData = m_interpreter->typed_input_tensor<float>(0);
        if (inputData) {
            // Pad or truncate to fit
            size_t maxSize = input->dims->data[1];
            size_t copySize = std::min(tokens.size(), maxSize);
            
            for (size_t i = 0; i < copySize; i++) {
                inputData[i] = tokens[i];
            }
            for (size_t i = copySize; i < maxSize; i++) {
                inputData[i] = 0.0f;
            }
        }
        
        // Run inference
        if (m_interpreter->Invoke() != kTfLiteOk) {
            LOGE("Failed to run inference");
            return result;
        }
        
        // Get output
        TfLiteTensor* output = m_interpreter->output_tensor(0);
        if (!output) {
            LOGE("Failed to get output tensor");
            return result;
        }
        
        // Postprocess output
        float* outputData = m_interpreter->typed_output_tensor<float>(0);
        if (outputData) {
            int outputSize = output->dims->data[1];
            
            // Collect scores
            result.all_scores.clear();
            for (int i = 0; i < outputSize; i++) {
                float score = outputData[i];
                if (i < m_intents.size()) {
                    result.all_scores.push_back({m_intents[i], score});
                } else {
                    result.all_scores.push_back({"intent_" + std::to_string(i), score});
                }
            }
            
            // Sort by confidence (descending)
            std::sort(result.all_scores.begin(), result.all_scores.end(),
                [](const auto& a, const auto& b) {
                    return a.second > b.second;
                });
            
            // Get top result
            if (!result.all_scores.empty() && result.all_scores[0].second > m_threshold) {
                result.intent = result.all_scores[0].first;
                result.confidence = result.all_scores[0].second;
            }
        }
        
    } catch (const std::exception& e) {
        LOGE("Classification error: %s", e.what());
    }
    
    return result;
}

std::string IntentClassifier::preprocessText(const std::string& text) {
    std::string result = text;
    
    // Convert to lowercase
    std::transform(result.begin(), result.end(), result.begin(), ::tolower);
    
    // Remove punctuation (simplified)
    std::string filtered;
    for (char c : result) {
        if (std::isalnum(c) || std::isspace(c)) {
            filtered += c;
        }
    }
    
    // Trim leading/trailing spaces
    size_t start = filtered.find_first_not_of(" \t");
    size_t end = filtered.find_last_not_of(" \t");
    
    if (start == std::string::npos) {
        return "";
    }
    
    return filtered.substr(start, end - start + 1);
}

std::vector<float> IntentClassifier::tokenize(const std::string& text) {
    std::vector<float> tokens;
    tokens.reserve(m_maxSeqLength);
    
    if (text.empty()) {
        tokens.push_back(0.0f); // Padding token
        return tokens;
    }
    
    // Simple tokenization: split by spaces
    std::istringstream iss(text);
    std::string word;
    
    while (iss >> word && tokens.size() < m_maxSeqLength) {
        // Map word to token ID (simplified - using hash)
        int tokenId = 0;
        auto it = m_vocab.find(word);
        if (it != m_vocab.end()) {
            tokenId = it->second;
        } else {
            // Unknown token
            tokenId = 1;
        }
        tokens.push_back(static_cast<float>(tokenId));
    }
    
    return tokens;
}

void IntentClassifier::buildVocabulary() {
    // Simplified vocabulary - in production, load from file
    // This is just a placeholder
    m_vocab[""] = 0;
    m_vocab["unknown"] = 1;
    m_vocab["open"] = 2;
    m_vocab["launch"] = 3;
    m_vocab["start"] = 4;
    m_vocab["type"] = 5;
    m_vocab["write"] = 6;
    m_vocab["enter"] = 7;
    m_vocab["search"] = 8;
    m_vocab["find"] = 9;
    m_vocab["help"] = 10;
    m_vocab["stop"] = 11;
    m_vocab["pause"] = 12;
    m_vocab["play"] = 13;
    m_vocab["next"] = 14;
    m_vocab["previous"] = 15;
}

} // namespace kate

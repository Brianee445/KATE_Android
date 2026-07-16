// app/src/main/cpp/intent/intent_classifier.h

#ifndef INTENT_CLASSIFIER_H
#define INTENT_CLASSIFIER_H

#include <string>
#include <vector>
#include <memory>
#include <unordered_map>

#include "tensorflow/lite/interpreter.h"
#include "tensorflow/lite/model.h"
#include "tensorflow/lite/kernels/register.h"

namespace kate {

struct ClassifierResult {
    std::string intent;
    float confidence;
    std::vector<std::pair<std::string, float>> all_scores;
};

class IntentClassifier {
public:
    IntentClassifier();
    ~IntentClassifier();
    
    bool loadModel(const std::string& modelPath);
    void unloadModel();
    bool isLoaded() const { return m_loaded; }
    
    ClassifierResult classify(const std::string& text);
    
    // Configuration
    void setTopK(int k) { m_topK = k; }
    void setThreshold(float threshold) { m_threshold = threshold; }
    
private:
    std::string preprocessText(const std::string& text);
    std::vector<float> tokenize(const std::string& text);
    ClassifierResult postprocess(const std::vector<float>& output);
    
    // TensorFlow Lite
    std::unique_ptr<tflite::FlatBufferModel> m_model;
    std::unique_ptr<tflite::Interpreter> m_interpreter;
    tflite::ops::builtin::BuiltinOpResolver m_resolver;
    
    std::atomic<bool> m_loaded{false};
    
    int m_topK = 3;
    float m_threshold = 0.3f;
    
    // Vocabulary (simplified - in production, use a proper tokenizer)
    std::unordered_map<std::string, int> m_vocab;
    std::vector<std::string> m_intents;
    int m_vocabSize = 10000;
    int m_maxSeqLength = 64;
    
    void buildVocabulary();
};

} // namespace kate

#endif // INTENT_CLASSIFIER_H

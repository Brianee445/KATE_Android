// app/src/main/cpp/intent/intent_engine.h

#ifndef INTENT_ENGINE_H
#define INTENT_ENGINE_H

#include <string>
#include <vector>
#include <memory>
#include <unordered_map>
#include <mutex>

#include "intent_classifier.h"
#include "entity_extractor.h"

namespace kate {

struct IntentResult {
    std::string intent;
    std::string action;
    std::string target;
    float confidence = 0.0f;
    std::vector<std::pair<std::string, std::string>> entities;
    std::string response;
    bool requires_payment = false;
};

class IntentEngine {
public:
    IntentEngine();
    ~IntentEngine();
    
    bool initialize(const std::string& modelPath);
    void shutdown();
    bool isInitialized() const { return m_initialized; }
    
    IntentResult classify(const std::string& text);
    
    // Rule-based fallback
    IntentResult ruleBasedClassify(const std::string& text);
    
    // Entity extraction
    std::vector<std::pair<std::string, std::string>> extractEntities(
        const std::string& text,
        const std::string& intent
    );
    
    // Response generation
    std::string generateResponse(const std::string& intent, 
                                  const std::vector<std::pair<std::string, std::string>>& entities);
    
    // Configuration
    void setConfidenceThreshold(float threshold) { m_confidenceThreshold = threshold; }
    void setUseML(bool use) { m_useML = use; }
    
private:
    std::unique_ptr<IntentClassifier> m_classifier;
    std::unique_ptr<EntityExtractor> m_entityExtractor;
    
    std::atomic<bool> m_initialized{false};
    std::atomic<bool> m_useML{true};
    
    float m_confidenceThreshold = 0.5f;
    
    // Intent response templates
    std::unordered_map<std::string, std::string> m_responseTemplates;
    void loadResponseTemplates();
    
    // Entity patterns
    std::unordered_map<std::string, std::vector<std::string>> m_entityPatterns;
    void loadEntityPatterns();
    
    mutable std::mutex m_mutex;
};

} // namespace kate

#endif // INTENT_ENGINE_H

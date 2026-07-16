// app/src/main/cpp/intent/intent_engine.h

#ifndef INTENT_ENGINE_H
#define INTENT_ENGINE_H

#include <string>
#include <vector>
#include <unordered_map>
#include <mutex>

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
    
    IntentResult classify(const std::string& text);
    IntentResult ruleBasedClassify(const std::string& text);
    
    std::vector<std::pair<std::string, std::string>> extractEntities(
        const std::string& text,
        const std::string& intent);
    
    std::string generateResponse(
        const std::string& intent,
        const std::vector<std::pair<std::string, std::string>>& entities);
    
private:
    void loadResponseTemplates();
    std::unordered_map<std::string, std::string> m_responseTemplates;
};

} // namespace kate

#endif // INTENT_ENGINE_H

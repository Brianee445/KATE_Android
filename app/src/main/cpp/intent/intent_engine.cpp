// app/src/main/cpp/intent/intent_engine.cpp

#include "intent_engine.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>
#include <fstream>
#include <nlohmann/json.hpp>

#define LOG_TAG "IntentEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using json = nlohmann::json;

namespace kate {

IntentEngine::IntentEngine() {
    loadResponseTemplates();
    loadEntityPatterns();
}

IntentEngine::~IntentEngine() {
    shutdown();
}

bool IntentEngine::initialize(const std::string& modelPath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_initialized) {
        return true;
    }
    
    // Initialize ML classifier if model exists
    m_classifier = std::make_unique<IntentClassifier>();
    if (m_classifier->loadModel(modelPath)) {
        LOGI("ML intent classifier loaded");
        m_useML = true;
    } else {
        LOGW("ML intent classifier not available, using rule-based only");
        m_useML = false;
        m_classifier.reset();
    }
    
    // Initialize entity extractor
    m_entityExtractor = std::make_unique<EntityExtractor>();
    if (!m_entityExtractor->initialize()) {
        LOGE("Failed to initialize entity extractor");
        // Continue anyway - we'll use fallback extraction
    }
    
    m_initialized = true;
    LOGI("IntentEngine initialized");
    return true;
}

void IntentEngine::shutdown() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_classifier) {
        m_classifier.reset();
    }
    
    if (m_entityExtractor) {
        m_entityExtractor.reset();
    }
    
    m_initialized = false;
    LOGI("IntentEngine shutdown");
}

IntentResult IntentEngine::classify(const std::string& text) {
    IntentResult result;
    
    if (!m_initialized) {
        return ruleBasedClassify(text);
    }
    
    // Try ML first
    if (m_useML && m_classifier) {
        result = m_classifier->classify(text);
        
        // Check if ML result is confident enough
        if (result.confidence >= m_confidenceThreshold) {
            // Extract entities
            result.entities = extractEntities(text, result.intent);
            
            // Generate response
            result.response = generateResponse(result.intent, result.entities);
            
            return result;
        }
    }
    
    // Fallback to rule-based
    result = ruleBasedClassify(text);
    
    // Extract entities
    result.entities = extractEntities(text, result.intent);
    
    // Generate response
    result.response = generateResponse(result.intent, result.entities);
    
    return result;
}

IntentResult IntentEngine::ruleBasedClassify(const std::string& text) {
    IntentResult result;
    result.intent = "unknown";
    result.confidence = 0.0f;
    result.action = "unknown";
    
    std::string lower = text;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
    
    // App opening
    if (lower.find("open") != std::string::npos || 
        lower.find("launch") != std::string::npos ||
        lower.find("start") != std::string::npos) {
        
        result.intent = "open_app";
        result.confidence = 0.75f;
        result.action = "open_app";
        
        // Try to extract app name
        for (const auto& trigger : {"open", "launch", "start", "run"}) {
            size_t pos = lower.find(trigger);
            if (pos != std::string::npos) {
                std::string app = lower.substr(pos + strlen(trigger));
                // Trim leading/trailing spaces
                app.erase(0, app.find_first_not_of(" \t"));
                app.erase(app.find_last_not_of(" \t") + 1);
                if (!app.empty()) {
                    result.target = app;
                    result.entities.push_back({"app", app});
                }
                break;
            }
        }
        
        if (result.target.empty()) {
            result.target = "the app";
        }
    }
    
    // Text typing
    else if (lower.find("type") != std::string::npos || 
             lower.find("write") != std::string::npos ||
             lower.find("enter") != std::string::npos ||
             lower.find("input") != std::string::npos) {
        
        result.intent = "type_text";
        result.confidence = 0.70f;
        result.action = "type_text";
        
        for (const auto& trigger : {"type", "write", "enter", "input"}) {
            size_t pos = lower.find(trigger);
            if (pos != std::string::npos) {
                std::string textToType = text.substr(pos + strlen(trigger));
                textToType.erase(0, textToType.find_first_not_of(" \t"));
                textToType.erase(textToType.find_last_not_of(" \t") + 1);
                if (!textToType.empty()) {
                    result.target = textToType;
                    result.entities.push_back({"text", textToType});
                }
                break;
            }
        }
    }
    
    // Search
    else if (lower.find("search") != std::string::npos || 
             lower.find("find") != std::string::npos ||
             lower.find("look up") != std::string::npos ||
             lower.find("google") != std::string::npos) {
        
        result.intent = "search";
        result.confidence = 0.65f;
        result.action = "search";
        
        for (const auto& trigger : {"search", "find", "look up", "google"}) {
            size_t pos = lower.find(trigger);
            if (pos != std::string::npos) {
                std::string query = text.substr(pos + strlen(trigger));
                query.erase(0, query.find_first_not_of(" \t"));
                query.erase(query.find_last_not_of(" \t") + 1);
                if (!query.empty() && query != "for" && query != "about") {
                    result.target = query;
                    result.entities.push_back({"query", query});
                }
                break;
            }
        }
    }
    
    // Help
    else if (lower.find("help") != std::string::npos || 
             lower.find("what can you do") != std::string::npos ||
             lower.find("commands") != std::string::npos ||
             lower.find("capabilities") != std::string::npos) {
        
        result.intent = "help";
        result.confidence = 0.85f;
        result.action = "help";
    }
    
    return result;
}

std::vector<std::pair<std::string, std::string>> IntentEngine::extractEntities(
    const std::string& text,
    const std::string& intent
) {
    std::vector<std::pair<std::string, std::string>> entities;
    
    if (m_entityExtractor) {
        entities = m_entityExtractor->extract(text, intent);
    }
    
    // Fallback entity extraction
    if (entities.empty()) {
        if (intent == "open_app") {
            std::string lower = text;
            std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
            
            for (const auto& trigger : {"open", "launch", "start", "run"}) {
                size_t pos = lower.find(trigger);
                if (pos != std::string::npos) {
                    std::string app = text.substr(pos + strlen(trigger));
                    app.erase(0, app.find_first_not_of(" \t"));
                    app.erase(app.find_last_not_of(" \t") + 1);
                    if (!app.empty()) {
                        entities.push_back({"app", app});
                    }
                    break;
                }
            }
        } else if (intent == "type_text") {
            for (const auto& trigger : {"type", "write", "enter", "input"}) {
                size_t pos = text.find(trigger);
                if (pos != std::string::npos) {
                    std::string textToType = text.substr(pos + strlen(trigger));
                    textToType.erase(0, textToType.find_first_not_of(" \t"));
                    textToType.erase(textToType.find_last_not_of(" \t") + 1);
                    if (!textToType.empty()) {
                        entities.push_back({"text", textToType});
                    }
                    break;
                }
            }
        }
    }
    
    return entities;
}

std::string IntentEngine::generateResponse(
    const std::string& intent,
    const std::vector<std::pair<std::string, std::string>>& entities
) {
    // Look up response template
    std::string template_str = "I'm not sure how to help with that.";
    
    auto it = m_responseTemplates.find(intent);
    if (it != m_responseTemplates.end()) {
        template_str = it->second;
    }
    
    // Replace placeholders with entity values
    std::string response = template_str;
    for (const auto& entity : entities) {
        std::string placeholder = "{" + entity.first + "}";
        size_t pos = response.find(placeholder);
        if (pos != std::string::npos) {
            response.replace(pos, placeholder.length(), entity.second);
        }
    }
    
    return response;
}

void IntentEngine::loadResponseTemplates() {
    m_responseTemplates = {
        {"open_app", "Opening {app}."},
        {"type_text", "Typing your text."},
        {"search", "Searching for {query}."},
        {"help", "I can open apps, type text, search, and more. Just ask!"},
        {"unknown", "I'm not sure how to help with that. Try asking differently."},
        {"premium_feature", "This feature requires a premium subscription. Upgrade to unlock it."}
    };
}

void IntentEngine::loadEntityPatterns() {
    m_entityPatterns = {
        {"app", {"open", "launch", "start"}},
        {"text", {"type", "write", "enter"}},
        {"query", {"search", "find", "look up"}},
    };
}

} // namespace kate

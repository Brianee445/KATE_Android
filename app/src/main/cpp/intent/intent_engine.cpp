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

// app/src/main/cpp/intent/intent_engine.cpp - Add new intents

// In the ruleBasedClassify method, add these cases:

// Torch/Flashlight
else if (lower.find("torch") != std::string::npos || 
         lower.find("flashlight") != std::string::npos ||
         lower.find("flash") != std::string::npos ||
         lower.find("light") != std::string::npos) {
    
    result.intent = "toggle_torch";
    result.confidence = 0.85f;
    result.action = "toggle_torch";
    
    // Check if turning on or off
    if (lower.find("on") != std::string::npos || 
        lower.find("enable") != std::string::npos) {
        result.entities.push_back({"state", "on"});
        result.response = "Turning torch on.";
    } else if (lower.find("off") != std::string::npos || 
               lower.find("disable") != std::string::npos) {
        result.entities.push_back({"state", "off"});
        result.response = "Turning torch off.";
    } else {
        result.response = "Toggling torch.";
    }
}

// Make Call
else if (lower.find("call") != std::string::npos || 
         lower.find("dial") != std::string::npos) {
    
    result.intent = "make_call";
    result.confidence = 0.80f;
    result.action = "make_call";
    
    // Extract phone number
    std::regex numberRegex(R"(\d{10,14})");
    std::smatch match;
    if (std::regex_search(text, match, numberRegex)) {
        result.target = match.str();
        result.entities.push_back({"number", match.str()});
        result.response = "Calling " + match.str() + ".";
    } else {
        result.response = "Please specify a phone number to call.";
        result.confidence = 0.4f;
    }
}

// Bluetooth
else if (lower.find("bluetooth") != std::string::npos) {
    result.intent = "toggle_bluetooth";
    result.confidence = 0.85f;
    result.action = "toggle_bluetooth";
    
    if (lower.find("on") != std::string::npos || 
        lower.find("enable") != std::string::npos) {
        result.entities.push_back({"state", "on"});
        result.response = "Turning Bluetooth on.";
    } else if (lower.find("off") != std::string::npos || 
               lower.find("disable") != std::string::npos) {
        result.entities.push_back({"state", "off"});
        result.response = "Turning Bluetooth off.";
    } else {
        result.response = "Toggling Bluetooth.";
    }
}

// Wi-Fi
else if (lower.find("wifi") != std::string::npos || 
         lower.find("wi-fi") != std::string::npos) {
    result.intent = "toggle_wifi";
    result.confidence = 0.85f;
    result.action = "toggle_wifi";
    
    if (lower.find("on") != std::string::npos || 
        lower.find("enable") != std::string::npos) {
        result.entities.push_back({"state", "on"});
        result.response = "Turning Wi-Fi on.";
    } else if (lower.find("off") != std::string::npos || 
               lower.find("disable") != std::string::npos) {
        result.entities.push_back({"state", "off"});
        result.response = "Turning Wi-Fi off.";
    } else {
        result.response = "Toggling Wi-Fi.";
    }
}

// Volume
else if (lower.find("volume") != std::string::npos) {
    result.intent = "set_volume";
    result.confidence = 0.80f;
    result.action = "set_volume";
    
    // Extract number
    std::regex numberRegex(R"(\d+)");
    std::smatch match;
    if (std::regex_search(text, match, numberRegex)) {
        result.target = match.str();
        result.entities.push_back({"level", match.str()});
        result.response = "Setting volume to " + match.str() + ".";
    } else if (lower.find("up") != std::string::npos || 
               lower.find("increase") != std::string::npos) {
        result.entities.push_back({"direction", "up"});
        result.response = "Increasing volume.";
    } else if (lower.find("down") != std::string::npos || 
               lower.find("decrease") != std::string::npos) {
        result.entities.push_back({"direction", "down"});
        result.response = "Decreasing volume.";
    } else if (lower.find("mute") != std::string::npos) {
        result.entities.push_back({"state", "mute"});
        result.response = "Muting volume.";
    } else {
        result.response = "Please specify volume level.";
        result.confidence = 0.4f;
    }
}

// Airplane Mode
else if (lower.find("airplane") != std::string::npos || 
         lower.find("flight") != std::string::npos) {
    result.intent = "toggle_airplane";
    result.confidence = 0.85f;
    result.action = "toggle_airplane";
    
    if (lower.find("on") != std::string::npos || 
        lower.find("enable") != std::string::npos) {
        result.entities.push_back({"state", "on"});
        result.response = "Turning airplane mode on.";
    } else if (lower.find("off") != std::string::npos || 
               lower.find("disable") != std::string::npos) {
        result.entities.push_back({"state", "off"});
        result.response = "Turning airplane mode off.";
    } else {
        result.response = "Toggling airplane mode.";
    }
}

// Do Not Disturb
else if (lower.find("do not disturb") != std::string::npos || 
         lower.find("dnd") != std::string::npos) {
    result.intent = "toggle_dnd";
    result.confidence = 0.85f;
    result.action = "toggle_dnd";
    
    if (lower.find("on") != std::string::npos || 
        lower.find("enable") != std::string::npos) {
        result.entities.push_back({"state", "on"});
        result.response = "Turning Do Not Disturb on.";
    } else if (lower.find("off") != std::string::npos || 
               lower.find("disable") != std::string::npos) {
        result.entities.push_back({"state", "off"});
        result.response = "Turning Do Not Disturb off.";
    } else {
        result.response = "Toggling Do Not Disturb.";
    }
}

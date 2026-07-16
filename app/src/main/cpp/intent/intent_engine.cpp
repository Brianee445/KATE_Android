// app/src/main/cpp/intent/intent_engine.cpp (Simplified - Rule-based only)

#include "intent_engine.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>
#include <regex>

#define LOG_TAG "IntentEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace kate {

IntentEngine::IntentEngine() {
    loadResponseTemplates();
}

IntentEngine::~IntentEngine() = default;

bool IntentEngine::initialize(const std::string& modelPath) {
    // Rule-based only - no TFLite
    LOGI("IntentEngine initialized (rule-based only)");
    return true;
}

void IntentEngine::shutdown() {
    LOGI("IntentEngine shutdown");
}

IntentResult IntentEngine::classify(const std::string& text) {
    return ruleBasedClassify(text);
}

IntentResult IntentEngine::ruleBasedClassify(const std::string& text) {
    IntentResult result;
    result.intent = "unknown";
    result.confidence = 0.0f;
    result.action = "unknown";
    
    std::string lower = text;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
    
    // ==================== OPEN APP ====================
    if (lower.find("open") != std::string::npos || 
        lower.find("launch") != std::string::npos ||
        lower.find("start") != std::string::npos ||
        lower.find("run") != std::string::npos) {
        
        result.intent = "open_app";
        result.confidence = 0.80f;
        result.action = "open_app";
        
        // Extract app name
        for (const auto& trigger : {"open ", "launch ", "start ", "run "}) {
            size_t pos = lower.find(trigger);
            if (pos != std::string::npos) {
                std::string app = text.substr(pos + strlen(trigger));
                app.erase(0, app.find_first_not_of(" \t"));
                app.erase(app.find_last_not_of(" \t") + 1);
                if (!app.empty()) {
                    result.target = app;
                    result.entities.push_back({"app", app});
                }
                break;
            }
        }
        result.response = "Opening " + (result.target.empty() ? "the app" : result.target);
    }
    
    // ==================== TYPE TEXT ====================
    else if (lower.find("type") != std::string::npos || 
             lower.find("write") != std::string::npos ||
             lower.find("enter") != std::string::npos) {
        
        result.intent = "type_text";
        result.confidence = 0.75f;
        result.action = "type_text";
        
        for (const auto& trigger : {"type ", "write ", "enter ", "input "}) {
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
        result.response = "Typing...";
    }
    
    // ==================== SEARCH ====================
    else if (lower.find("search") != std::string::npos || 
             lower.find("find") != std::string::npos) {
        
        result.intent = "search";
        result.confidence = 0.70f;
        result.action = "search";
        
        for (const auto& trigger : {"search ", "find ", "look up "}) {
            size_t pos = lower.find(trigger);
            if (pos != std::string::npos) {
                std::string query = text.substr(pos + strlen(trigger));
                query.erase(0, query.find_first_not_of(" \t"));
                query.erase(query.find_last_not_of(" \t") + 1);
                if (!query.empty()) {
                    result.target = query;
                    result.entities.push_back({"query", query});
                }
                break;
            }
        }
        result.response = "Searching...";
    }
    
    // ==================== TORCH ====================
    else if (lower.find("torch") != std::string::npos || 
             lower.find("flashlight") != std::string::npos ||
             lower.find("flash") != std::string::npos) {
        
        result.intent = "toggle_torch";
        result.confidence = 0.85f;
        result.action = "toggle_torch";
        
        if (lower.find("on") != std::string::npos) {
            result.response = "Turning torch on.";
        } else if (lower.find("off") != std::string::npos) {
            result.response = "Turning torch off.";
        } else {
            result.response = "Toggling torch.";
        }
    }
    
    // ==================== CALL ====================
    else if (lower.find("call") != std::string::npos || 
             lower.find("dial") != std::string::npos) {
        
        result.intent = "make_call";
        result.confidence = 0.80f;
        result.action = "make_call";
        
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
    
    // ==================== BLUETOOTH ====================
    else if (lower.find("bluetooth") != std::string::npos) {
        result.intent = "toggle_bluetooth";
        result.confidence = 0.85f;
        result.action = "toggle_bluetooth";
        
        if (lower.find("on") != std::string::npos) {
            result.response = "Turning Bluetooth on.";
        } else if (lower.find("off") != std::string::npos) {
            result.response = "Turning Bluetooth off.";
        } else {
            result.response = "Toggling Bluetooth.";
        }
    }
    
    // ==================== WI-FI ====================
    else if (lower.find("wifi") != std::string::npos || 
             lower.find("wi-fi") != std::string::npos) {
        result.intent = "toggle_wifi";
        result.confidence = 0.85f;
        result.action = "toggle_wifi";
        
        if (lower.find("on") != std::string::npos) {
            result.response = "Turning Wi-Fi on.";
        } else if (lower.find("off") != std::string::npos) {
            result.response = "Turning Wi-Fi off.";
        } else {
            result.response = "Toggling Wi-Fi.";
        }
    }
    
    // ==================== VOLUME ====================
    else if (lower.find("volume") != std::string::npos) {
        result.intent = "set_volume";
        result.confidence = 0.75f;
        result.action = "set_volume";
        
        std::regex numberRegex(R"(\d+)");
        std::smatch match;
        if (std::regex_search(text, match, numberRegex)) {
            result.target = match.str();
            result.entities.push_back({"level", match.str()});
            result.response = "Setting volume to " + match.str() + ".";
        } else if (lower.find("up") != std::string::npos || lower.find("increase") != std::string::npos) {
            result.response = "Increasing volume.";
        } else if (lower.find("down") != std::string::npos || lower.find("decrease") != std::string::npos) {
            result.response = "Decreasing volume.";
        } else {
            result.response = "Please specify volume level.";
            result.confidence = 0.4f;
        }
    }
    
    // ==================== HELP ====================
    else if (lower.find("help") != std::string::npos || 
             lower.find("what can you do") != std::string::npos) {
        result.intent = "help";
        result.confidence = 0.90f;
        result.action = "help";
        result.response = "I can open apps, type text, search, control torch, Bluetooth, Wi-Fi, make calls, and more!";
    }
    
    return result;
}

std::vector<std::pair<std::string, std::string>> IntentEngine::extractEntities(
    const std::string& text,
    const std::string& intent) {
    
    std::vector<std::pair<std::string, std::string>> entities;
    // Simple entity extraction - can be expanded
    return entities;
}

std::string IntentEngine::generateResponse(
    const std::string& intent,
    const std::vector<std::pair<std::string, std::string>>& entities) {
    
    auto it = m_responseTemplates.find(intent);
    if (it != m_responseTemplates.end()) {
        std::string response = it->second;
        for (const auto& entity : entities) {
            std::string placeholder = "{" + entity.first + "}";
            size_t pos = response.find(placeholder);
            if (pos != std::string::npos) {
                response.replace(pos, placeholder.length(), entity.second);
            }
        }
        return response;
    }
    return "I'm not sure how to help with that.";
}

void IntentEngine::loadResponseTemplates() {
    m_responseTemplates = {
        {"open_app", "Opening {app}."},
        {"type_text", "Typing your text."},
        {"search", "Searching for {query}."},
        {"toggle_torch", "Toggling torch."},
        {"make_call", "Calling {number}."},
        {"toggle_bluetooth", "Toggling Bluetooth."},
        {"toggle_wifi", "Toggling Wi-Fi."},
        {"set_volume", "Setting volume."},
        {"help", "I can open apps, type text, search, control torch, Bluetooth, Wi-Fi, make calls, and more!"},
        {"unknown", "I'm not sure how to help with that."}
    };
}

} // namespace kate

// app/src/main/cpp/intent/entity_extractor.cpp

#include "entity_extractor.h"
#include <android/log.h>
#include <algorithm>
#include <sstream>

#define LOG_TAG "EntityExtractor"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace kate {

EntityExtractor::EntityExtractor() {
    buildPatterns();
}

bool EntityExtractor::initialize() {
    LOGI("EntityExtractor initialized");
    return true;
}

std::vector<std::pair<std::string, std::string>> EntityExtractor::extract(
    const std::string& text,
    const std::string& intent
) {
    std::vector<std::pair<std::string, std::string>> entities;
    
    if (intent == "open_app") {
        std::string app = extractAppName(text);
        if (!app.empty()) {
            entities.push_back({"app", app});
        }
    } else if (intent == "type_text") {
        std::string textToType = extractTextToType(text);
        if (!textToType.empty()) {
            entities.push_back({"text", textToType});
        }
    } else if (intent == "search") {
        std::string query = extractSearchQuery(text);
        if (!query.empty()) {
            entities.push_back({"query", query});
        }
    }
    
    // Try to extract numbers from any intent
    std::string number = extractNumber(text);
    if (!number.empty()) {
        entities.push_back({"number", number});
    }
    
    return entities;
}

std::string EntityExtractor::extractAppName(const std::string& text) {
    std::string lower = text;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
    
    std::vector<std::string> triggers = {"open", "launch", "start", "run"};
    
    for (const auto& trigger : triggers) {
        size_t pos = lower.find(trigger);
        if (pos != std::string::npos) {
            // Find the start of the app name (after the trigger + spaces)
            size_t start = pos + trigger.length();
            while (start < lower.length() && lower[start] == ' ') {
                start++;
            }
            
            if (start < lower.length()) {
                std::string app = text.substr(start);
                // Remove trailing articles
                size_t end = app.find_last_not_of(" \t");
                if (end != std::string::npos) {
                    app = app.substr(0, end + 1);
                }
                // Remove common articles from end
                for (const auto& article : {" please", " now", " quickly"}) {
                    if (app.length() > strlen(article)) {
                        std::string lowerApp = app;
                        std::transform(lowerApp.begin(), lowerApp.end(), lowerApp.begin(), ::tolower);
                        size_t articlePos = lowerApp.find(article);
                        if (articlePos != std::string::npos) {
                            app = app.substr(0, articlePos);
                            break;
                        }
                    }
                }
                return app;
            }
        }
    }
    
    return "";
}

std::string EntityExtractor::extractTextToType(const std::string& text) {
    std::vector<std::string> triggers = {"type", "write", "enter", "input"};
    
    for (const auto& trigger : triggers) {
        size_t pos = text.find(trigger);
        if (pos != std::string::npos) {
            size_t start = pos + trigger.length();
            while (start < text.length() && text[start] == ' ') {
                start++;
            }
            
            if (start < text.length()) {
                std::string textToType = text.substr(start);
                // Remove trailing commands
                size_t end = textToType.find_last_not_of(" \t");
                if (end != std::string::npos) {
                    textToType = textToType.substr(0, end + 1);
                }
                // Remove common trailing phrases
                for (const auto& phrase : {" now", " please", " quickly"}) {
                    if (textToType.length() > strlen(phrase)) {
                        std::string lowerType = textToType;
                        std::transform(lowerType.begin(), lowerType.end(), lowerType.begin(), ::tolower);
                        size_t phrasePos = lowerType.find(phrase);
                        if (phrasePos != std::string::npos) {
                            textToType = textToType.substr(0, phrasePos);
                            break;
                        }
                    }
               

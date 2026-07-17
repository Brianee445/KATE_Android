// app/src/main/cpp/cache/conversation_cache.cpp

#include "conversation_cache.h"
#include "conversation_record.h"  // For ConversationRecord
#include <android/log.h>
#include <algorithm>
#include <sstream>
#include <cmath>
#include <fstream>
#include <regex>

#define LOG_TAG "ConversationCache"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace kate {

ConversationCache::ConversationCache() = default;

ConversationCache::~ConversationCache() {
    shutdown();
}

bool ConversationCache::initialize(const std::string& dbPath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_initialized) {
        return true;
    }
    
    if (!dbPath.empty()) {
        loadFromFile(dbPath);
    }
    
    m_initialized = true;
    LOGI("ConversationCache initialized");
    return true;
}

void ConversationCache::shutdown() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_cache.clear();
    while (!m_accessOrder.empty()) {
        m_accessOrder.pop();
    }
    m_initialized = false;
    LOGI("ConversationCache shutdown");
}

bool ConversationCache::store(const std::string& query, const std::string& response, 
                               const std::string& intent, float confidence) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    std::string normalized = normalizeQuery(query);
    if (normalized.empty() || response.empty()) {
        return false;
    }
    
    CacheEntry entry;
    entry.query = query;
    entry.response = response;
    entry.intent = intent;
    entry.confidence = confidence;
    entry.timestamp = std::chrono::steady_clock::now();
    entry.accessCount = 0;
    
    m_cache[normalized] = entry;
    m_accessOrder.push(normalized);
    
    trim(m_maxSize);
    
    return true;
}

bool ConversationCache::storeConversation(const ConversationRecord& record) {
    return store(record.query, record.response, record.intent, record.confidence);
}

std::string ConversationCache::getResponse(const std::string& query) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    std::string normalized = normalizeQuery(query);
    if (normalized.empty()) {
        return "";
    }
    
    auto it = m_cache.find(normalized);
    if (it != m_cache.end()) {
        it->second.accessCount++;
        return it->second.response;
    }
    
    auto similar = findSimilar(query, 1);
    if (!similar.empty() && similar[0].confidence >= m_similarityThreshold) {
        return similar[0].response;
    }
    
    return "";
}

void ConversationCache::clear() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_cache.clear();
    while (!m_accessOrder.empty()) {
        m_accessOrder.pop();
    }
    LOGI("Cache cleared");
}

void ConversationCache::trim(size_t maxSize) {
    if (m_cache.size() <= maxSize) {
        return;
    }
    
    size_t toRemove = m_cache.size() - maxSize;
    for (size_t i = 0; i < toRemove && !m_accessOrder.empty(); i++) {
        std::string key = m_accessOrder.front();
        m_accessOrder.pop();
        
        auto it = m_cache.find(key);
        if (it != m_cache.end()) {
            m_cache.erase(it);
        }
    }
    
    LOGD("Trimmed cache to %zu entries", m_cache.size());
}

std::vector<CacheEntry> ConversationCache::findSimilar(const std::string& query, int limit) {
    std::vector<CacheEntry> results;
    
    if (m_cache.empty()) {
        return results;
    }
    
    std::string normalized = normalizeQuery(query);
    if (normalized.empty()) {
        return results;
    }
    
    std::vector<std::pair<float, CacheEntry>> scored;
    for (const auto& pair : m_cache) {
        float similarity = calculateSimilarity(normalized, pair.first);
        if (similarity >= m_similarityThreshold) {
            CacheEntry entry = pair.second;
            scored.push_back({similarity, entry});
        }
    }
    
    std::sort(scored.begin(), scored.end(),
        [](const auto& a, const auto& b) {
            return a.first > b.first;
        });
    
    int count = std::min(limit, static_cast<int>(scored.size()));
    for (int i = 0; i < count; i++) {
        results.push_back(scored[i].second);
    }
    
    return results;
}

std::string ConversationCache::normalizeQuery(const std::string& query) {
    std::string result = query;
    std::transform(result.begin(), result.end(), result.begin(), ::tolower);
    
    std::stringstream ss(result);
    std::string word;
    std::string normalized;
    while (ss >> word) {
        if (!normalized.empty()) {
            normalized += " ";
        }
        normalized += word;
    }
    
    return normalized;
}

float ConversationCache::calculateSimilarity(const std::string& a, const std::string& b) {
    if (a.empty() || b.empty()) {
        return 0.0f;
    }
    
    auto tokensA = tokenize(a);
    auto tokensB = tokenize(b);
    
    if (tokensA.empty() || tokensB.empty()) {
        return 0.0f;
    }
    
    std::unordered_map<std::string, int> freqA;
    for (const auto& token : tokensA) {
        freqA[token]++;
    }
    
    int intersection = 0;
    for (const auto& token : tokensB) {
        if (freqA.find(token) != freqA.end()) {
            intersection++;
        }
    }
    
    int unionSize = tokensA.size() + tokensB.size() - intersection;
    if (unionSize == 0) {
        return 0.0f;
    }
    
    return static_cast<float>(intersection) / unionSize;
}

std::vector<std::string> ConversationCache::tokenize(const std::string& text) {
    std::vector<std::string> tokens;
    std::stringstream ss(text);
    std::string token;
    
    while (ss >> token) {
        token.erase(std::remove_if(token.begin(), token.end(),
            [](char c) { return std::ispunct(c); }),
            token.end());
        
        if (!token.empty()) {
            tokens.push_back(token);
        }
    }
    
    return tokens;
}

// ==================== JSON ESCAPING HELPER ====================
static std::string escapeJsonString(const std::string& str) {
    std::string result;
    result.reserve(str.size());
    for (char c : str) {
        switch (c) {
            case '"':  result += "\\\""; break;
            case '\\': result += "\\\\"; break;
            case '\b': result += "\\b";  break;
            case '\f': result += "\\f";  break;
            case '\n': result += "\\n";  break;
            case '\r': result += "\\r";  break;
            case '\t': result += "\\t";  break;
            default:
                if (c < 32) {
                    result += "\\u" + std::to_string(static_cast<unsigned char>(c));
                } else {
                    result += c;
                }
                break;
        }
    }
    return result;
}

// ==================== SAVE TO FILE ====================
bool ConversationCache::saveToFile(const std::string& path) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    std::ofstream file(path);
    if (!file.is_open()) {
        LOGE("Failed to open cache file for writing: %s", path.c_str());
        return false;
    }
    
    // Write JSON manually (no external library)
    file << "[\n";
    size_t index = 0;
    for (const auto& pair : m_cache) {
        const auto& entry = pair.second;
        file << "  {\n";
        file << "    \"query\": \"" << escapeJsonString(entry.query) << "\",\n";
        file << "    \"response\": \"" << escapeJsonString(entry.response) << "\",\n";
        file << "    \"intent\": \"" << escapeJsonString(entry.intent) << "\",\n";
        file << "    \"confidence\": " << std::fixed << entry.confidence << ",\n";
        file << "    \"access_count\": " << entry.accessCount << "\n";
        file << "  }";
        if (index < m_cache.size() - 1) {
            file << ",";
        }
        file << "\n";
        index++;
    }
    file << "]\n";
    
    file.close();
    LOGI("Cache saved to %s (%zu entries)", path.c_str(), m_cache.size());
    return true;
}

// ==================== LOAD FROM FILE ====================
bool ConversationCache::loadFromFile(const std::string& path) {
    std::ifstream file(path);
    if (!file.is_open()) {
        LOGD("Cache file not found: %s", path.c_str());
        return false;
    }
    
    // Read entire file
    std::string content((std::istreambuf_iterator<char>(file)),
                         std::istreambuf_iterator<char>());
    file.close();
    
    if (content.empty()) {
        return false;
    }
    
    // Simple parsing - find each object in the array
    std::lock_guard<std::mutex> lock(m_mutex);
    m_cache.clear();
    
    // Look for "query" fields and extract data
    // This is a simplified parser that extracts key:value pairs
    size_t pos = 0;
    int entriesFound = 0;
    
    while ((pos = content.find("\"query\"", pos)) != std::string::npos) {
        // Find the colon after "query"
        size_t colonPos = content.find(":", pos);
        if (colonPos == std::string::npos) break;
        
        // Find the opening quote of the query value
        size_t queryStart = content.find("\"", colonPos + 1);
        if (queryStart == std::string::npos) break;
        size_t queryEnd = content.find("\"", queryStart + 1);
        if (queryEnd == std::string::npos) break;
        
        std::string query = content.substr(queryStart + 1, queryEnd - queryStart - 1);
        
        // Find "response"
        size_t respPos = content.find("\"response\"", queryEnd);
        if (respPos == std::string::npos) break;
        
        size_t respColon = content.find(":", respPos);
        if (respColon == std::string::npos) break;
        
        size_t respStart = content.find("\"", respColon + 1);
        if (respStart == std::string::npos) break;
        size_t respEnd = content.find("\"", respStart + 1);
        if (respEnd == std::string::npos) break;
        
        std::string response = content.substr(respStart + 1, respEnd - respStart - 1);
        
        // Find "intent"
        size_t intentPos = content.find("\"intent\"", respEnd);
        if (intentPos == std::string::npos) break;
        
        size_t intentColon = content.find(":", intentPos);
        if (intentColon == std::string::npos) break;
        
        size_t intentStart = content.find("\"", intentColon + 1);
        if (intentStart == std::string::npos) break;
        size_t intentEnd = content.find("\"", intentStart + 1);
        if (intentEnd == std::string::npos) break;
        
        std::string intent = content.substr(intentStart + 1, intentEnd - intentStart - 1);
        
        // Find "confidence"
        size_t confPos = content.find("\"confidence\"", intentEnd);
        if (confPos == std::string::npos) break;
        
        size_t confColon = content.find(":", confPos);
        if (confColon == std::string::npos) break;
        
        // Find the number (up to comma or newline)
        size_t confStart = confColon + 1;
        while (confStart < content.length() && (content[confStart] == ' ' || content[confStart] == '\n')) {
            confStart++;
        }
        size_t confEnd = confStart;
        while (confEnd < content.length() && content[confEnd] != ',' && content[confEnd] != '\n' && content[confEnd] != '}') {
            confEnd++;
        }
        std::string confStr = content.substr(confStart, confEnd - confStart);
        float confidence = 0.0f;
        try {
            confidence = std::stof(confStr);
        } catch (...) {
            confidence = 0.0f;
        }
        
        // Find "access_count"
        size_t accessPos = content.find("\"access_count\"", confEnd);
        if (accessPos == std::string::npos) break;
        
        size_t accessColon = content.find(":", accessPos);
        if (accessColon == std::string::npos) break;
        
        size_t accessStart = accessColon + 1;
        while (accessStart < content.length() && (content[accessStart] == ' ' || content[accessStart] == '\n')) {
            accessStart++;
        }
        size_t accessEnd = accessStart;
        while (accessEnd < content.length() && content[accessEnd] != ',' && content[accessEnd] != '\n' && content[accessEnd] != '}') {
            accessEnd++;
        }
        std::string accessStr = content.substr(accessStart, accessEnd - accessStart);
        int accessCount = 0;
        try {
            accessCount = std::stoi(accessStr);
        } catch (...) {
            accessCount = 0;
        }
        
        // Store the entry
        CacheEntry entry;
        entry.query = query;
        entry.response = response;
        entry.intent = intent;
        entry.confidence = confidence;
        entry.accessCount = accessCount;
        entry.timestamp = std::chrono::steady_clock::now();
        
        if (!entry.query.empty() && !entry.response.empty()) {
            std::string normalized = normalizeQuery(entry.query);
            m_cache[normalized] = entry;
            entriesFound++;
        }
        
        pos = queryEnd + 1;
    }
    
    LOGI("Cache loaded from %s (%d entries)", path.c_str(), entriesFound);
    return true;
}

} // namespace kate

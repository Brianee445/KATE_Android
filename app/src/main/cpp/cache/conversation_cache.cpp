// app/src/main/cpp/cache/conversation_cache.cpp

#include "conversation_cache.h"
#include <android/log.h>
#include <algorithm>
#include <sstream>
#include <cmath>
#include <fstream>
#include <nlohmann/json.hpp>

#define LOG_TAG "ConversationCache"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using json = nlohmann::json;

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
    
    // Try to load from file
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
    
    // Trim if needed
    trim(m_maxSize);
    
    return true;
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
    
    // Try similarity search
    auto similar = findSimilar(query, 1);
    if (!similar.empty() && similar[0].confidence >= m_similarityThreshold) {
        return similar[0].response;
    }
    
    return "";
}

bool ConversationCache::storeConversation(const ConversationRecord& record) {
    return store(record.query, record.response, record.intent, record.confidence);
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
    // Don't trim if within limit
    if (m_cache.size() <= maxSize) {
        return;
    }
    
    // Remove oldest entries until under limit
    size_t toRemove = m_cache.size() - maxSize;
    for (size_t i = 0; i < toRemove && !m_accessOrder.empty(); i++) {
        std::string key = m_accessOrder.front();
        m_accessOrder.pop();
        
        // Check if key still exists (might have been updated)
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
    
    // Calculate similarity for each entry
    std::vector<std::pair<float, CacheEntry>> scored;
    for (const auto& pair : m_cache) {
        float similarity = calculateSimilarity(normalized, pair.first);
        if (similarity >= m_similarityThreshold) {
            CacheEntry entry = pair.second;
            scored.push_back({similarity, entry});
        }
    }
    
    // Sort by similarity (descending)
    std::sort(scored.begin(), scored.end(),
        [](const auto& a, const auto& b) {
            return a.first > b.first;
        });
    
    // Take top N
    int count = std::min(limit, static_cast<int>(scored.size()));
    for (int i = 0; i < count; i++) {
        results.push_back(scored[i].second);
    }
    
    return results;
}

std::string ConversationCache::normalizeQuery(const std::string& query) {
    std::string result = query;
    
    // Convert to lowercase
    std::transform(result.begin(), result.end(), result.begin(), ::tolower);
    
    // Remove extra spaces
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
    
    // Jaccard similarity on tokens
    auto tokensA = tokenize(a);
    auto tokensB = tokenize(b);
    
    if (tokensA.empty() || tokensB.empty()) {
        return 0.0f;
    }
    
    // Count intersection
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
        // Remove punctuation (simplified)
        token.erase(std::remove_if(token.begin(), token.end(),
            [](char c) { return std::ispunct(c); }),
            token.end());
        
        if (!token.empty()) {
            tokens.push_back(token);
        }
    }
    
    return tokens;
}

bool ConversationCache::saveToFile(const std::string& path) {
    try {
        json data = json::array();
        
        std::lock_guard<std::mutex> lock(m_mutex);
        for (const auto& pair : m_cache) {
            json entry;
            entry["query"] = pair.second.query;
            entry["response"] = pair.second.response;
            entry["intent"] = pair.second.intent;
            entry["confidence"] = pair.second.confidence;
            entry["access_count"] = pair.second.accessCount;
            data.push_back(entry);
        }
        
        std::ofstream file(path);
        if (!file.is_open()) {
            LOGE("Failed to open cache file for writing: %s", path.c_str());
            return false;
        }
        
        file << data.dump(2);
        file.close();
        
        LOGI("Cache saved to %s (%zu entries)", path.c_str(), m_cache.size());
        return true;
        
    } catch (const std::exception& e) {
        LOGE("Failed to save cache: %s", e.what());
        return false;
    }
}

bool ConversationCache::loadFromFile(const std::string& path) {
    try {
        std::ifstream file(path);
        if (!file.is_open()) {
            LOGD("Cache file not found: %s", path.c_str());
            return false;
        }
        
        json data = json::parse(file);
        
        std::lock_guard<std::mutex> lock(m_mutex);
        m_cache.clear();
        
        for (const auto& entry : data) {
            CacheEntry cacheEntry;
            cacheEntry.query = entry.value("query", "");
            cacheEntry.response = entry.value("response", "");
            cacheEntry.intent = entry.value("intent", "");
            cacheEntry.confidence = entry.value("confidence", 0.0f);
            cacheEntry.accessCount = entry.value("access_count", 0);
            cacheEntry.timestamp = std::chrono::steady_clock::now();
            
            if (!cacheEntry.query.empty() && !cacheEntry.response.empty()) {
                std::string normalized = normalizeQuery(cacheEntry.query);
                m_cache[normalized] = cacheEntry;
            }
        }
        
        LOGI("Cache loaded from %s (%zu entries)", path.c_str(), m_cache.size());
        return true;
        
    } catch (const std::exception& e) {
        LOGE("Failed to load cache: %s", e.what());
        return false;
    }
}

} // namespace kate

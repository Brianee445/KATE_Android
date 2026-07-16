// app/src/main/cpp/cache/conversation_cache.h

#ifndef CONVERSATION_CACHE_H
#define CONVERSATION_CACHE_H

#include <string>
#include <vector>
#include <unordered_map>
#include <mutex>
#include <queue>
#include <chrono>

namespace kate {

// Forward declaration of ConversationRecord
// Full definition is in kate_engine.h
struct ConversationRecord;

struct CacheEntry {
    std::string query;
    std::string response;
    std::string intent;
    float confidence;
    std::chrono::steady_clock::time_point timestamp;
    int accessCount;
    
    CacheEntry() : confidence(0.0f), accessCount(0) {}
};

class ConversationCache {
public:
    ConversationCache();
    ~ConversationCache();
    
    bool initialize(const std::string& dbPath);
    void shutdown();
    bool isInitialized() const { return m_initialized; }
    
    // Core operations
    bool store(const std::string& query, const std::string& response, 
               const std::string& intent = "", float confidence = 0.0f);
    std::string getResponse(const std::string& query);
    bool storeConversation(const ConversationRecord& record);
    
    // Management
    void clear();
    void trim(size_t maxSize = 1000);
    size_t size() const { return m_cache.size(); }
    
    // Similarity search
    std::vector<CacheEntry> findSimilar(const std::string& query, int limit = 3);
    
    // Persistence
    bool saveToFile(const std::string& path);
    bool loadFromFile(const std::string& path);
    
    // Configuration
    void setMaxSize(size_t max) { m_maxSize = max; }
    void setSimilarityThreshold(float threshold) { m_similarityThreshold = threshold; }
    
private:
    std::string normalizeQuery(const std::string& query);
    float calculateSimilarity(const std::string& a, const std::string& b);
    std::vector<std::string> tokenize(const std::string& text);
    
    std::unordered_map<std::string, CacheEntry> m_cache;
    std::queue<std::string> m_accessOrder;
    
    std::atomic<bool> m_initialized{false};
    size_t m_maxSize = 1000;
    float m_similarityThreshold = 0.6f;
    
    mutable std::mutex m_mutex;
};

} // namespace kate

#endif // CONVERSATION_CACHE_H

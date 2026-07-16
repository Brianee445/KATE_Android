// app/src/main/cpp/intent/entity_extractor.h

#ifndef ENTITY_EXTRACTOR_H
#define ENTITY_EXTRACTOR_H

#include <string>
#include <vector>
#include <utility>
#include <unordered_map>
#include <regex>

namespace kate {

class EntityExtractor {
public:
    EntityExtractor();
    ~EntityExtractor() = default;
    
    bool initialize();
    
    std::vector<std::pair<std::string, std::string>> extract(
        const std::string& text,
        const std::string& intent
    );
    
    // Entity-specific extractors
    std::string extractAppName(const std::string& text);
    std::string extractTextToType(const std::string& text);
    std::string extractSearchQuery(const std::string& text);
    std::string extractNumber(const std::string& text);
    
private:
    // Patterns
    std::vector<std::pair<std::string, std::regex>> m_patterns;
    std::unordered_map<std::string, std::vector<std::string>> m_entityPatterns;
    
    void buildPatterns();
};

} // namespace kate

#endif // ENTITY_EXTRACTOR_H

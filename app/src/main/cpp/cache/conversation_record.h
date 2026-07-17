// app/src/main/cpp/cache/conversation_record.h

#ifndef CONVERSATION_RECORD_H
#define CONVERSATION_RECORD_H

#include <string>
#include <cstdint>

namespace kate {

struct ConversationRecord {
    std::string query;
    std::string response;
    std::string intent;
    float confidence = 0.0f;
    int64_t timestamp = 0; // milliseconds since epoch

    ConversationRecord() = default;
};

} // namespace kate

#endif // CONVERSATION_RECORD_H

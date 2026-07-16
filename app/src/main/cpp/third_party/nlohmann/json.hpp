// app/src/main/cpp/third_party/nlohmann/json.hpp
// Simplified single-header JSON library
// Full version: https://github.com/nlohmann/json/releases

#ifndef NLOHMANN_JSON_HPP
#define NLOHMANN_JSON_HPP

#include <string>
#include <vector>
#include <map>
#include <memory>
#include <sstream>
#include <iomanip>
#include <algorithm>
#include <cctype>

namespace nlohmann {

class json {
public:
    enum class value_t {
        null,
        object,
        array,
        string,
        boolean,
        number_integer,
        number_float,
        discarded
    };

    // Constructors
    json() : m_type(value_t::null) {}
    json(const std::string& s) : m_type(value_t::string), m_string(s) {}
    json(const char* s) : m_type(value_t::string), m_string(s) {}
    json(bool b) : m_type(value_t::boolean), m_boolean(b) {}
    json(int i) : m_type(value_t::number_integer), m_number_int(i) {}
    json(double d) : m_type(value_t::number_float), m_number_float(d) {}
    json(const json& other) { copy(other); }
    
    ~json() = default;

    // Assignment
    json& operator=(const json& other) {
        copy(other);
        return *this;
    }

    // Type checking
    bool is_null() const { return m_type == value_t::null; }
    bool is_object() const { return m_type == value_t::object; }
    bool is_array() const { return m_type == value_t::array; }
    bool is_string() const { return m_type == value_t::string; }
    bool is_boolean() const { return m_type == value_t::boolean; }
    bool is_number() const { return is_number_integer() || is_number_float(); }
    bool is_number_integer() const { return m_type == value_t::number_integer; }
    bool is_number_float() const { return m_type == value_t::number_float; }

    // Object access
    json& operator[](const std::string& key) {
        if (m_type == value_t::null) {
            m_type = value_t::object;
        }
        if (m_type != value_t::object) {
            throw std::runtime_error("Not an object");
        }
        return m_object[key];
    }

    const json& operator[](const std::string& key) const {
        if (m_type != value_t::object) {
            throw std::runtime_error("Not an object");
        }
        auto it = m_object.find(key);
        if (it == m_object.end()) {
            throw std::runtime_error("Key not found");
        }
        return it->second;
    }

    bool contains(const std::string& key) const {
        if (m_type != value_t::object) {
            return false;
        }
        return m_object.find(key) != m_object.end();
    }

    // Array access
    json& operator[](size_t index) {
        if (m_type == value_t::null) {
            m_type = value_t::array;
        }
        if (m_type != value_t::array) {
            throw std::runtime_error("Not an array");
        }
        if (index >= m_array.size()) {
            throw std::runtime_error("Index out of bounds");
        }
        return m_array[index];
    }

    const json& operator[](size_t index) const {
        if (m_type != value_t::array) {
            throw std::runtime_error("Not an array");
        }
        if (index >= m_array.size()) {
            throw std::runtime_error("Index out of bounds");
        }
        return m_array[index];
    }

    void push_back(const json& value) {
        if (m_type == value_t::null) {
            m_type = value_t::array;
        }
        if (m_type != value_t::array) {
            throw std::runtime_error("Not an array");
        }
        m_array.push_back(value);
    }

    size_t size() const {
        if (m_type == value_t::object) return m_object.size();
        if (m_type == value_t::array) return m_array.size();
        return 0;
    }

    // Value access
    std::string get_string() const {
        if (m_type != value_t::string) {
            throw std::runtime_error("Not a string");
        }
        return m_string;
    }

    bool get_boolean() const {
        if (m_type != value_t::boolean) {
            throw std::runtime_error("Not a boolean");
        }
        return m_boolean;
    }

    int get_int() const {
        if (m_type != value_t::number_integer) {
            throw std::runtime_error("Not an integer");
        }
        return m_number_int;
    }

    double get_float() const {
        if (m_type != value_t::number_float) {
            throw std::runtime_error("Not a float");
        }
        return m_number_float;
    }

    // Value access with default
    std::string value(const std::string& key, const std::string& default_value) const {
        if (!contains(key)) return default_value;
        try {
            return (*this)[key].get_string();
        } catch (...) {
            return default_value;
        }
    }

    int value(const std::string& key, int default_value) const {
        if (!contains(key)) return default_value;
        try {
            return (*this)[key].get_int();
        } catch (...) {
            return default_value;
        }
    }

    float value(const std::string& key, float default_value) const {
        if (!contains(key)) return default_value;
        try {
            return static_cast<float>((*this)[key].get_float());
        } catch (...) {
            return default_value;
        }
    }

    bool value(const std::string& key, bool default_value) const {
        if (!contains(key)) return default_value;
        try {
            return (*this)[key].get_boolean();
        } catch (...) {
            return default_value;
        }
    }

    // JSON serialization
    std::string dump(int indent = -1) const {
        std::stringstream ss;
        dump(ss, indent);
        return ss.str();
    }

    // JSON parsing
    static json parse(const std::string& str) {
        json result;
        size_t pos = 0;
        parse_value(str, pos, result);
        return result;
    }

    static json parse(std::istream& is) {
        std::string str;
        char c;
        while (is.get(c)) {
            str += c;
        }
        return parse(str);
    }

private:
    void copy(const json& other) {
        m_type = other.m_type;
        m_string = other.m_string;
        m_boolean = other.m_boolean;
        m_number_int = other.m_number_int;
        m_number_float = other.m_number_float;
        m_object = other.m_object;
        m_array = other.m_array;
    }

    void dump(std::stringstream& ss, int indent, int depth = 0) const {
        std::string indent_str(indent > 0 ? depth * indent : 0, ' ');

        switch (m_type) {
            case value_t::null:
                ss << "null";
                break;
            case value_t::object: {
                ss << "{";
                if (indent > 0) ss << "\n";
                size_t i = 0;
                for (const auto& pair : m_object) {
                    if (indent > 0) ss << indent_str << "  ";
                    ss << "\"" << pair.first << "\":";
                    if (indent > 0) ss << " ";
                    pair.second.dump(ss, indent, depth + 1);
                    if (++i < m_object.size()) ss << ",";
                    if (indent > 0) ss << "\n";
                }
                if (indent > 0) ss << indent_str;
                ss << "}";
                break;
            }
            case value_t::array: {
                ss << "[";
                if (indent > 0) ss << "\n";
                for (size_t i = 0; i < m_array.size(); i++) {
                    if (indent > 0) ss << indent_str << "  ";
                    m_array[i].dump(ss, indent, depth + 1);
                    if (i + 1 < m_array.size()) ss << ",";
                    if (indent > 0) ss << "\n";
                }
                if (indent > 0) ss << indent_str;
                ss << "]";
                break;
            }
            case value_t::string:
                ss << "\"" << escape_string(m_string) << "\"";
                break;
            case value_t::boolean:
                ss << (m_boolean ? "true" : "false");
                break;
            case value_t::number_integer:
                ss << m_number_int;
                break;
            case value_t::number_float:
                ss << std::fixed << std::setprecision(6) << m_number_float;
                break;
            default:
                break;
        }
    }

    static std::string escape_string(const std::string& s) {
        std::string result;
        for (char c : s) {
            switch (c) {
                case '"': result += "\\\""; break;
                case '\\': result += "\\\\"; break;
                case '\b': result += "\\b"; break;
                case '\f': result += "\\f"; break;
                case '\n': result += "\\n"; break;
                case '\r': result += "\\r"; break;
                case '\t': result += "\\t"; break;
                default:
                    if (c < 32) {
                        char buf[8];
                        snprintf(buf, sizeof(buf), "\\u%04x", static_cast<unsigned char>(c));
                        result += buf;
                    } else {
                        result += c;
                    }
                    break;
            }
        }
        return result;
    }

    static void parse_value(const std::string& str, size_t& pos, json& result) {
        // Skip whitespace
        while (pos < str.length() && std::isspace(str[pos])) pos++;

        if (pos >= str.length()) return;

        char c = str[pos];

        if (c == '{') {
            result = json::parse_object(str, pos);
        } else if (c == '[') {
            result = json::parse_array(str, pos);
        } else if (c == '"') {
            result = json::parse_string(str, pos);
        } else if (c == 't' && str.compare(pos, 4, "true") == 0) {
            result = true;
            pos += 4;
        } else if (c == 'f' && str.compare(pos, 5, "false") == 0) {
            result = false;
            pos += 5;
        } else if (c == 'n' && str.compare(pos, 4, "null") == 0) {
            result = json();
            pos += 4;
        } else if (c == '-' || std::isdigit(c)) {
            result = json::parse_number(str, pos);
        }
    }

    static json parse_object(const std::string& str, size_t& pos) {
        json result;
        result.m_type = value_t::object;

        pos++; // Skip '{'

        while (pos < str.length()) {
            while (pos < str.length() && std::isspace(str[pos])) pos++;

            if (str[pos] == '}') {
                pos++;
                break;
            }

            if (str[pos] != '"') {
                throw std::runtime_error("Expected key string");
            }

            json key = parse_string(str, pos);

            while (pos < str.length() && std::isspace(str[pos])) pos++;

            if (str[pos] != ':') {
                throw std::runtime_error("Expected ':' after key");
            }
            pos++;

            json value;
            parse_value(str, pos, value);

            result.m_object[key.m_string] = value;

            while (pos < str.length() && std::isspace(str[pos])) pos++;

            if (str[pos] == ',') {
                pos++;
                continue;
            } else if (str[pos] == '}') {
                pos++;
                break;
            } else {
                throw std::runtime_error("Expected ',' or '}' after value");
            }
        }

        return result;
    }

    static json parse_array(const std::string& str, size_t& pos) {
        json result;
        result.m_type = value_t::array;

        pos++; // Skip '['

        while (pos < str.length()) {
            while (pos < str.length() && std::isspace(str[pos])) pos++;

            if (str[pos] == ']') {
                pos++;
                break;
            }

            json value;
            parse_value(str, pos, value);
            result.m_array.push_back(value);

            while (pos < str.length() && std::isspace(str[pos])) pos++;

            if (str[pos] == ',') {
                pos++;
                continue;
            } else if (str[pos] == ']') {
                pos++;
                break;
            } else {
                throw std::runtime_error("Expected ',' or ']' after value");
            }
        }

        return result;
    }

    static json parse_string(const std::string& str, size_t& pos) {
        json result;
        result.m_type = value_t::string;

        pos++; // Skip opening '"'

        std::string value;
        while (pos < str.length()) {
            char c = str[pos];
            if (c == '"') {
                pos++;
                break;
            }
            if (c == '\\') {
                pos++;
                if (pos >= str.length()) break;
                c = str[pos];
                switch (c) {
                    case '"': value += '"'; break;
                    case '\\': value += '\\'; break;
                    case '/': value += '/'; break;
                    case 'b': value += '\b'; break;
                    case 'f': value += '\f'; break;
                    case 'n': value += '\n'; break;
                    case 'r': value += '\r'; break;
                    case 't': value += '\t'; break;
                    case 'u': {
                        // Unicode escape - simplified
                        pos++;
                        std::string hex;
                        for (int i = 0; i < 4 && pos < str.length(); i++) {
                            hex += str[pos++];
                        }
                        char unicode = static_cast<char>(std::stoi(hex, nullptr, 16));
                        value += unicode;
                        pos--;
                        break;
                    }
                    default: value += c; break;
                }
            } else {
                value += c;
            }
            pos++;
        }

        result.m_string = value;
        return result;
    }

    static json parse_number(const std::string& str, size_t& pos) {
        json result;
        bool is_float = false;
        std::string num_str;

        if (str[pos] == '-') {
            num_str += str[pos++];
        }

        while (pos < str.length() && std::isdigit(str[pos])) {
            num_str += str[pos++];
        }

        if (pos < str.length() && str[pos] == '.') {
            is_float = true;
            num_str += str[pos++];
            while (pos < str.length() && std::isdigit(str[pos])) {
                num_str += str[pos++];
            }
        }

        if (pos < str.length() && (str[pos] == 'e' || str[pos] == 'E')) {
            is_float = true;
            num_str += str[pos++];
            if (pos < str.length() && (str[pos] == '+' || str[pos] == '-')) {
                num_str += str[pos++];
            }
            while (pos < str.length() && std::isdigit(str[pos])) {
                num_str += str[pos++];
            }
        }

        if (is_float) {
            result.m_type = value_t::number_float;
            result.m_number_float = std::stod(num_str);
        } else {
            result.m_type = value_t::number_integer;
            result.m_number_int = std::stoi(num_str);
        }

        return result;
    }

private:
    value_t m_type = value_t::null;
    std::string m_string;
    bool m_boolean = false;
    int m_number_int = 0;
    double m_number_float = 0.0;
    std::map<std::string, json> m_object;
    std::vector<json> m_array;
};

} // namespace nlohmann

#endif // NLOHMANN_JSON_HPP

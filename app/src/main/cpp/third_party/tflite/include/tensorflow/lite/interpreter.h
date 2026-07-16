// app/src/main/cpp/third_party/tflite/include/tensorflow/lite/interpreter.h

#ifndef TENSORFLOW_LITE_INTERPRETER_H
#define TENSORFLOW_LITE_INTERPRETER_H

#include <cstddef>
#include <cstdint>
#include <vector>
#include <memory>
#include <string>

namespace tflite {

// Forward declarations
class ErrorReporter;
class Interpreter;
class Model;
class OpResolver;

// ==================== Error Reporting ====================

class ErrorReporter {
public:
    virtual ~ErrorReporter() = default;
    virtual int Report(const char* format, ...) = 0;
};

// ==================== Tensor ====================

enum TfLiteType {
    kTfLiteNoType = 0,
    kTfLiteFloat32 = 1,
    kTfLiteInt32 = 2,
    kTfLiteUInt8 = 3,
    kTfLiteInt64 = 4,
    kTfLiteInt16 = 5,
    kTfLiteFloat16 = 6,
    kTfLiteBool = 7,
    kTfLiteComplex64 = 8,
    kTfLiteInt8 = 9,
};

struct TfLiteIntArray {
    int size;
    int data[];
};

struct TfLiteTensor {
    TfLiteType type;
    int dimensions;
    TfLiteIntArray* dims;
    void* data;
    size_t bytes;
    bool is_variable;
    void* allocation;
    const char* name;
};

// ==================== Interpreter ====================

class Interpreter {
public:
    Interpreter() = default;
    virtual ~Interpreter() = default;

    // Allocate tensors
    virtual TfLiteStatus AllocateTensors() = 0;
    
    // Get input/output tensor count
    virtual int inputs() const = 0;
    virtual int outputs() const = 0;
    
    // Get input/output tensor
    virtual const TfLiteTensor* input_tensor(int index) const = 0;
    virtual TfLiteTensor* input_tensor(int index) = 0;
    virtual const TfLiteTensor* output_tensor(int index) const = 0;
    virtual TfLiteTensor* output_tensor(int index) = 0;
    
    // Typed tensor accessors
    template <typename T>
    T* typed_input_tensor(int index) {
        TfLiteTensor* tensor = input_tensor(index);
        if (tensor) {
            return reinterpret_cast<T*>(tensor->data);
        }
        return nullptr;
    }
    
    template <typename T>
    T* typed_output_tensor(int index) {
        TfLiteTensor* tensor = output_tensor(index);
        if (tensor) {
            return reinterpret_cast<T*>(tensor->data);
        }
        return nullptr;
    }
    
    // Set input tensor data
    virtual TfLiteStatus SetInputTensorData(int index, const void* data, size_t size) = 0;
    
    // Get output tensor data
    virtual TfLiteStatus GetOutputTensorData(int index, void* data, size_t size) = 0;
    
    // Invoke inference
    virtual TfLiteStatus Invoke() = 0;
    
    // Reset variables
    virtual void ResetVariableTensors() = 0;
    
    // Get tensor name
    virtual const char* GetTensorName(int index) const = 0;
    
    // Get tensor index by name
    virtual int GetTensorIndex(const char* name) const = 0;
};

// ==================== Model ====================

class FlatBufferModel {
public:
    FlatBufferModel() = default;
    virtual ~FlatBufferModel() = default;
    
    // Build from file
    static std::unique_ptr<FlatBufferModel> BuildFromFile(const char* filename, ErrorReporter* error_reporter = nullptr);
    
    // Build from buffer
    static std::unique_ptr<FlatBufferModel> BuildFromBuffer(const char* buffer_data, size_t buffer_size, ErrorReporter* error_reporter = nullptr);
    
    // Check if model is valid
    virtual bool initialized() const = 0;
};

// ==================== Op Resolver ====================

class OpResolver {
public:
    virtual ~OpResolver() = default;
    
    // Add built-in operations
    virtual void AddBuiltin(int op, void* registration, int min_version = 1, int max_version = 1) = 0;
    virtual void AddCustom(const char* name, void* registration, int min_version = 1, int max_version = 1) = 0;
};

// ==================== Interpreter Builder ====================

class InterpreterBuilder {
public:
    InterpreterBuilder(const FlatBufferModel& model, const OpResolver& op_resolver);
    virtual ~InterpreterBuilder() = default;
    
    // Build interpreter
    virtual TfLiteStatus operator()(std::unique_ptr<Interpreter>* interpreter) = 0;
};

// ==================== Status Codes ====================

enum TfLiteStatus {
    kTfLiteOk = 0,
    kTfLiteError = 1,
    kTfLiteDelegateError = 2,
    kTfLiteApplicationError = 3,
    kTfLiteDelegateDataError = 4,
};

} // namespace tflite

#endif // TENSORFLOW_LITE_INTERPRETER_H

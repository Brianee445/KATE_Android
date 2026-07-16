// app/src/main/cpp/third_party/tflite/include/tensorflow/lite/kernels/register.h

#ifndef TENSORFLOW_LITE_KERNELS_REGISTER_H
#define TENSORFLOW_LITE_KERNELS_REGISTER_H

#include "../interpreter.h"

namespace tflite {
namespace ops {
namespace builtin {

class BuiltinOpResolver : public OpResolver {
public:
    BuiltinOpResolver();
    ~BuiltinOpResolver() override = default;
    
    void AddBuiltin(int op, void* registration, int min_version = 1, int max_version = 1) override;
    void AddCustom(const char* name, void* registration, int min_version = 1, int max_version = 1) override;
    
    // Get built-in operations
    void* FindBuiltin(int op) const;
    void* FindCustom(const char* name) const;
};

} // namespace builtin
} // namespace ops
} // namespace tflite

#endif // TENSORFLOW_LITE_KERNELS_REGISTER_H

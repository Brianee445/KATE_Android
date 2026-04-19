#include "tflite_runner.h"
#include <tensorflow/lite/c/c_api.h>
#include <string.h>
#include <stdlib.h>

static TfLiteModel* model = NULL;
static TfLiteInterpreter* interpreter = NULL;

void tflite_init(const char* model_path) {
    model = TfLiteModelCreateFromFile(model_path);

    TfLiteInterpreterOptions* options = TfLiteInterpreterOptionsCreate();
    TfLiteInterpreterOptionsSetNumThreads(options, 2);

    interpreter = TfLiteInterpreterCreate(model, options);
    TfLiteInterpreterAllocateTensors(interpreter);

    TfLiteInterpreterOptionsDelete(options);
}

int tflite_predict(const char* text) {

    if (!interpreter) return -1;

    TfLiteTensor* input = TfLiteInterpreterGetInputTensor(interpreter, 0);

    // VERY SIMPLE ENCODING (replace later with real tokenizer)
    float buffer[32] = {0};

    int len = strlen(text);
    for (int i = 0; i < len && i < 32; i++) {
        buffer[i] = (float)(text[i]) / 255.0f;
    }

    TfLiteTensorCopyFromBuffer(input, buffer, sizeof(buffer));

    TfLiteInterpreterInvoke(interpreter);

    const TfLiteTensor* output = TfLiteInterpreterGetOutputTensor(interpreter, 0);

    float scores[8];
    TfLiteTensorCopyToBuffer(output, scores, sizeof(scores));

    // Get max index
    int best = 0;
    float max = scores[0];

    for (int i = 1; i < 8; i++) {
        if (scores[i] > max) {
            max = scores[i];
            best = i;
        }
    }

    return best;
}

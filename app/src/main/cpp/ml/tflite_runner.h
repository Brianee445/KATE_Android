#ifndef TFLITE_RUNNER_H
#define TFLITE_RUNNER_H

void tflite_init(const char* model_path);
int tflite_predict(const char* text);

#endif

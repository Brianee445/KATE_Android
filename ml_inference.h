#ifndef ML_INFERENCE_H
#define ML_INFERENCE_H

void ml_init(const char* model_path);
const char* ml_predict_intent(const char* text);

#endif

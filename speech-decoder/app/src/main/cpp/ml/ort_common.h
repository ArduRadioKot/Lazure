#pragma once
#include <onnxruntime_cxx_api.h>
#include <string>
#include <vector>

inline Ort::SessionOptions makeSessionOptions(int intraThreads) {
    Ort::SessionOptions so;
    so.SetIntraOpNumThreads(intraThreads);
    so.SetInterOpNumThreads(1);
    so.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
    so.AddConfigEntry("session.intra_op.allow_spinning", "0");   // не греть CPU холостым спином
    // Для ускорения можно добавить EP: so.AppendExecutionProvider("XNNPACK", {...}) / NNAPI.
    return so;
}

inline std::string inputName(Ort::Session& s, size_t i) {
    Ort::AllocatorWithDefaultOptions a;
    return s.GetInputNameAllocated(i, a).get();
}
inline std::string outputName(Ort::Session& s, size_t i) {
    Ort::AllocatorWithDefaultOptions a;
    return s.GetOutputNameAllocated(i, a).get();
}
inline size_t inputRank(Ort::Session& s, size_t i) {
    return s.GetInputTypeInfo(i).GetTensorTypeAndShapeInfo().GetShape().size();
}
inline size_t outputRank(Ort::Session& s, size_t i) {
    return s.GetOutputTypeInfo(i).GetTensorTypeAndShapeInfo().GetShape().size();
}

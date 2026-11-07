#pragma once
// Lock-free single-producer / single-consumer кольцо. Ёмкость — степень двойки.
// Никаких аллокаций после конструктора. T должен быть trivially copyable.
#include <atomic>
#include <vector>
#include <cstring>
#include <cstddef>
#include <algorithm>

template <typename T>
class SpscRing {
public:
    explicit SpscRing(size_t capacityPow2) : buf_(capacityPow2), mask_(capacityPow2 - 1) {}

    size_t write(const T* src, size_t n) {
        const size_t w = w_.load(std::memory_order_relaxed);
        const size_t r = r_.load(std::memory_order_acquire);
        n = std::min(n, buf_.size() - (w - r));
        const size_t first = std::min(n, buf_.size() - (w & mask_));
        std::memcpy(&buf_[w & mask_], src, first * sizeof(T));
        std::memcpy(&buf_[0], src + first, (n - first) * sizeof(T));
        w_.store(w + n, std::memory_order_release);
        return n;
    }

    size_t read(T* dst, size_t n) {
        const size_t r = r_.load(std::memory_order_relaxed);
        const size_t w = w_.load(std::memory_order_acquire);
        n = std::min(n, w - r);
        const size_t first = std::min(n, buf_.size() - (r & mask_));
        std::memcpy(dst, &buf_[r & mask_], first * sizeof(T));
        std::memcpy(dst + first, &buf_[0], (n - first) * sizeof(T));
        r_.store(r + n, std::memory_order_release);
        return n;
    }

    size_t available() const {
        return w_.load(std::memory_order_acquire) - r_.load(std::memory_order_acquire);
    }

private:
    std::vector<T> buf_;
    size_t mask_;
    alignas(64) std::atomic<size_t> w_{0};
    alignas(64) std::atomic<size_t> r_{0};
};

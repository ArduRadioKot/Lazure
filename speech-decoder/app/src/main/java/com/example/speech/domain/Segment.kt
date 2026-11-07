package com.example.speech.domain

data class Segment(val startMs: Long, val endMs: Long, val speaker: Int, val text: String)

package com.benchfirewatch.ui

/** A number the field may save. Out of range and unreadable text are refused. */
fun acceptedNumber(text: String, min: Float, max: Float): Float? {
    if (min > max) return null
    val value = text.trim().toFloatOrNull() ?: return null
    if (!value.isFinite() || value < min || value > max) return null
    return value
}

/** Whole numbers only, so 1.5 is not stored as 1. */
fun acceptedWholeNumber(text: String, min: Int, max: Int): Int? {
    val value = acceptedNumber(text, min.toFloat(), max.toFloat()) ?: return null
    val whole = value.toInt()
    if (whole.toFloat() != value) return null
    return whole
}

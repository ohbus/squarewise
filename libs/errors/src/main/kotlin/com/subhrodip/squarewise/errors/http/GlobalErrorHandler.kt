package com.subhrodip.squarewise.errors.http

import com.subhrodip.squarewise.errors.web.GlobalErrorAdvice

/**
 * Compatibility alias for callers still importing the old handler package.
 *
 * The implementation lives only in [GlobalErrorAdvice] so servlet applications
 * cannot accidentally select a second, less-safe renderer.
 */
typealias GlobalErrorHandler = GlobalErrorAdvice

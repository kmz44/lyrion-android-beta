/*
 * Copyright (C) 2024 Lyrion
 * Performance utilities for optimal app performance
 */

package io.orabel.orabelandroid.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import kotlin.system.measureTimeMillis

object PerformanceUtils {
    private const val TAG = "PerformanceUtils"
    
    /**
     * Log memory usage information
     */
    fun logMemoryUsage(context: Context, tag: String = "MemoryUsage") {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val nativeHeapSize = Debug.getNativeHeapSize()
        val nativeHeapAllocatedSize = Debug.getNativeHeapAllocatedSize()
        val nativeHeapFreeSize = Debug.getNativeHeapFreeSize()
        
        Log.d(tag, """
            Memory Status:
            - Available memory: ${memoryInfo.availMem / 1024 / 1024} MB
            - Total memory: ${memoryInfo.totalMem / 1024 / 1024} MB
            - Low memory: ${memoryInfo.lowMemory}
            - Native heap size: ${nativeHeapSize / 1024 / 1024} MB
            - Native heap allocated: ${nativeHeapAllocatedSize / 1024 / 1024} MB
            - Native heap free: ${nativeHeapFreeSize / 1024 / 1024} MB
        """.trimIndent())
    }
    
    /**
     * Force garbage collection (use sparingly)
     */
    fun forceGarbageCollection() {
        System.gc()
        System.runFinalization()
    }
    
    /**
     * Measure execution time of a block
     */
    fun <T> measureTime(tag: String, block: () -> T): T {
        var result: T
        val time = kotlin.system.measureTimeMillis {
            result = block()
        }
        Log.d(TAG, "$tag executed in ${time}ms")
        return result
    }
    
    /**
     * Check if device is running low on memory
     */
    fun isLowMemory(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }
    
    /**
     * Get device memory class
     */
    fun getMemoryClass(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.memoryClass
    }
    
    /**
     * Get large memory class if available
     */
    fun getLargeMemoryClass(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.largeMemoryClass
    }
}

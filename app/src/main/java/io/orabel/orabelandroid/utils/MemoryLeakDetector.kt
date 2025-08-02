/*
 * Copyright (C) 2024 Lyrion
 * Memory leak detection utilities
 */

package io.orabel.orabelandroid.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import java.lang.ref.WeakReference

object MemoryLeakDetector : Application.ActivityLifecycleCallbacks {
    private const val TAG = "MemoryLeakDetector"
    private val activityReferences = mutableSetOf<WeakReference<Activity>>()
    
    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activityReferences.add(WeakReference(activity))
        Log.d(TAG, "Activity created: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityStarted(activity: Activity) {
        Log.d(TAG, "Activity started: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityResumed(activity: Activity) {
        Log.d(TAG, "Activity resumed: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityPaused(activity: Activity) {
        Log.d(TAG, "Activity paused: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityStopped(activity: Activity) {
        Log.d(TAG, "Activity stopped: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.d(TAG, "Activity save instance state: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityDestroyed(activity: Activity) {
        Log.d(TAG, "Activity destroyed: ${activity.javaClass.simpleName}")
        
        // Clean up weak references
        activityReferences.removeAll { it.get() == null }
        
        // Check for potential memory leaks
        checkForLeaks()
    }
    
    private fun checkForLeaks() {
        // Force garbage collection
        System.gc()
        System.runFinalization()
        
        // Check for leaked activities
        val leakedActivities = activityReferences.filter { it.get() != null }
        if (leakedActivities.isNotEmpty()) {
            Log.w(TAG, "Potential memory leak detected: ${leakedActivities.size} activities not garbage collected")
            leakedActivities.forEach { ref ->
                ref.get()?.let { activity ->
                    Log.w(TAG, "Leaked activity: ${activity.javaClass.simpleName}")
                }
            }
        }
    }
}

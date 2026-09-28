package com.roadseye.dashcam.settingssection

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// Core keys
val CLIP_DURATION_KEY = stringPreferencesKey("clip_duration")
val CRASH_DETECTION_KEY = booleanPreferencesKey("crash_detection")
val AUTO_RECORD_KEY = booleanPreferencesKey("auto_record")
val SPEED_THRESHOLD_KEY = floatPreferencesKey("speed_threshold")
val DURATION_THRESHOLD_KEY = floatPreferencesKey("duration_threshold")
val SPEED_UNITS_KEY = stringPreferencesKey("speed_units")

// Video & Camera settings
val VIDEO_QUALITY_KEY = stringPreferencesKey("video_quality")
val FPS_KEY = stringPreferencesKey("frames_per_second")
val CAMERA_ZOOM_KEY = stringPreferencesKey("camera_zoom")

// Appearance: "System" (default), "Light" or "Dark"
val APPEARANCE_KEY = stringPreferencesKey("appearance")

// Overlay settings
val DRIVING_OVERLAY_MODE_KEY = booleanPreferencesKey("driving_overlay_mode")
val OVERLAY_ALWAYS_ACTIVE_KEY = booleanPreferencesKey("overlay_always_active")

// Audio settings
val IS_VOICE_RECORDING_KEY = booleanPreferencesKey("is_voice_recording")

val SEGMENT_DURATION_KEY = stringPreferencesKey("segment_duration")

val MAX_STORAGE_MB_KEY = intPreferencesKey("max_storage_mb")

val PIP_ENABLED_KEY = booleanPreferencesKey("pip_enabled")

// Lifetime driving stats (shown in the Account tab)
val STAT_DISTANCE_METERS_KEY = doublePreferencesKey("stat_distance_meters")
val STAT_RECORDING_TIME_MS_KEY = longPreferencesKey("stat_recording_time_ms")
val STAT_TOP_SPEED_MS_KEY = doublePreferencesKey("stat_top_speed_ms")
val STAT_DRIVES_KEY = intPreferencesKey("stat_drives")
val STAT_VIDEOS_SAVED_KEY = intPreferencesKey("stat_videos_saved")
val STAT_CLIPS_SAVED_KEY = intPreferencesKey("stat_clips_saved")
val STAT_CRASH_CLIPS_SAVED_KEY = intPreferencesKey("stat_crash_clips_saved")

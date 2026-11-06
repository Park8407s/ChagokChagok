package com.kumhoht.todaysost.engine.synth

/** libostsynth.so (app/src/main/cpp/ostsynth_jni.cpp) 바인딩. 직접 쓰지 말고 [OfflineRenderer] 를 쓴다. */
internal object FluidSynthNative {
    init {
        // libfluidsynth.so 와 그 의존성은 libostsynth.so 를 올릴 때 함께 올라온다
        System.loadLibrary("ostsynth")
    }

    @JvmStatic external fun nativeCreate(sampleRate: Int): Long
    @JvmStatic external fun nativeLoadSoundFont(handle: Long, path: String): Int
    @JvmStatic external fun nativeRender(handle: Long, events: IntArray, totalFrames: Int): FloatArray?
    @JvmStatic external fun nativeDestroy(handle: Long)
}

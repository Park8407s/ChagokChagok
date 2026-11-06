// FluidSynth 오프라인 렌더 JNI 래퍼.
// 오디오 장치를 열지 않고, 이벤트 목록을 받아 곡 전체를 float 스테레오 버퍼로 렌더한다.

#include <jni.h>
#include <android/log.h>
#include <fluidsynth.h>

#include <algorithm>
#include <vector>

#define TAG "OstSynth"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Engine {
    fluid_settings_t *settings = nullptr;
    fluid_synth_t *synth = nullptr;
};

// 이벤트 한 개 = int 6개: frame, type, channel, a, b, c  (Kotlin SynthEvent 와 같은 순서)
constexpr int kEventInts = 6;
enum EventType {
    NOTE_ON = 0,        // a=key, b=velocity
    NOTE_OFF = 1,       // a=key
    PROGRAM_SELECT = 2, // a=sfontId, b=bank, c=program
    CONTROL_CHANGE = 3, // a=controller, b=value
    PITCH_BEND = 4,     // a=0..16383 (8192 = 가운데)
};

// FluidSynth 로그를 logcat 으로 (기본은 stderr 라 안드로이드에서 안 보임)
void fluidLog(int level, const char *message, void *) {
    int prio = level <= FLUID_ERR ? ANDROID_LOG_ERROR
             : level == FLUID_WARN ? ANDROID_LOG_WARN
             : level == FLUID_INFO ? ANDROID_LOG_INFO : ANDROID_LOG_DEBUG;
    __android_log_print(prio, "FluidSynth", "%s", message);
}

Engine *engine(jlong handle) { return reinterpret_cast<Engine *>(handle); }

void apply(fluid_synth_t *s, const jint *e) {
    const int ch = e[2];
    switch (e[1]) {
        case NOTE_ON: fluid_synth_noteon(s, ch, e[3], e[4]); break;
        case NOTE_OFF: fluid_synth_noteoff(s, ch, e[3]); break;
        case PROGRAM_SELECT:
            if (fluid_synth_program_select(s, ch, e[3], e[4], e[5]) != FLUID_OK)
                LOGE("program_select 실패: ch=%d sfont=%d bank=%d prog=%d", ch, e[3], e[4], e[5]);
            break;
        case CONTROL_CHANGE: fluid_synth_cc(s, ch, e[3], e[4]); break;
        case PITCH_BEND: fluid_synth_pitch_bend(s, ch, e[3]); break;
        default: LOGE("알 수 없는 이벤트 %d", e[1]);
    }
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_kumhoht_todaysost_engine_synth_FluidSynthNative_nativeCreate(JNIEnv *, jclass, jint sampleRate) {
    for (int level = FLUID_PANIC; level <= FLUID_INFO; level++) fluid_set_log_function(level, fluidLog, nullptr);

    auto *e = new Engine();
    e->settings = new_fluid_settings();
    fluid_settings_setnum(e->settings, "synth.sample-rate", sampleRate);
    fluid_settings_setint(e->settings, "synth.polyphony", 256);
    fluid_settings_setint(e->settings, "synth.reverb.active", 1);
    fluid_settings_setint(e->settings, "synth.chorus.active", 1);
    // 여러 악기가 겹쳐도 클리핑이 덜 나도록 기본(0.2)보다 조금만 올린다. 최종 음량은 마스터링에서 맞춘다.
    fluid_settings_setnum(e->settings, "synth.gain", 0.3);
    e->synth = new_fluid_synth(e->settings);
    if (!e->synth) {
        LOGE("new_fluid_synth 실패");
        delete_fluid_settings(e->settings);
        delete e;
        return 0;
    }
    LOGI("FluidSynth %s, %d Hz", fluid_version_str(), sampleRate);
    return reinterpret_cast<jlong>(e);
}

/** @return 사운드폰트 ID (실패하면 -1) */
JNIEXPORT jint JNICALL
Java_com_kumhoht_todaysost_engine_synth_FluidSynthNative_nativeLoadSoundFont(JNIEnv *env, jclass, jlong handle, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    // reset_presets=0: 채널 프리셋은 PROGRAM_SELECT 이벤트로 직접 고른다
    int id = fluid_synth_sfload(engine(handle)->synth, p, 0);
    if (id == FLUID_FAILED) LOGE("사운드폰트 로드 실패: %s", p);
    env->ReleaseStringUTFChars(path, p);
    return id == FLUID_FAILED ? -1 : id;
}

/**
 * 이벤트(프레임 순으로 정렬됨)를 적용하며 totalFrames 만큼 렌더한다.
 * @return 인터리브 스테레오 float (L,R,L,R...), 길이 totalFrames*2
 */
JNIEXPORT jfloatArray JNICALL
Java_com_kumhoht_todaysost_engine_synth_FluidSynthNative_nativeRender(JNIEnv *env, jclass, jlong handle,
                                                                      jintArray events, jint totalFrames) {
    fluid_synth_t *s = engine(handle)->synth;
    const jsize n = env->GetArrayLength(events) / kEventInts;
    std::vector<jint> ev(static_cast<size_t>(n) * kEventInts);
    env->GetIntArrayRegion(events, 0, n * kEventInts, ev.data());

    std::vector<float> out(static_cast<size_t>(totalFrames) * 2);
    int pos = 0, idx = 0;
    while (pos < totalFrames) {
        while (idx < n && ev[idx * kEventInts] <= pos) apply(s, &ev[idx++ * kEventInts]);
        int next = idx < n ? std::min<int>(ev[idx * kEventInts], totalFrames) : totalFrames;
        int len = next - pos;
        if (len > 0) {
            fluid_synth_write_float(s, len, out.data(), pos * 2, 2, out.data(), pos * 2 + 1, 2);
            pos = next;
        }
    }
    // 다음 렌더를 위해 남은 소리를 모두 끈다
    fluid_synth_system_reset(s);

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(out.size()));
    if (result) env->SetFloatArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    return result;
}

JNIEXPORT void JNICALL
Java_com_kumhoht_todaysost_engine_synth_FluidSynthNative_nativeDestroy(JNIEnv *, jclass, jlong handle) {
    Engine *e = engine(handle);
    if (!e) return;
    delete_fluid_synth(e->synth);
    delete_fluid_settings(e->settings);
    delete e;
}

} // extern "C"

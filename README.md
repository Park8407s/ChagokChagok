# 오늘의 OST (Today's OST)

차속, 가감속, 깜빡이, 정차, 과속 같은 **주행 데이터를 규칙 기반 작곡 엔진으로 연주**해서 운전할 때마다 세상에 하나뿐인 트랙을 만드는 Pleos Connect 차량용 앱입니다.
도착하면 오늘의 트랙을 들려주고, 하루 동안 만든 트랙을 모아 '오늘의 OST' 앨범으로 남깁니다.

> **같은 길이라도 운전 습관에 따라 곡이 달라집니다.**

생성형 AI를 쓰지 않아 곡당 비용이 없고, 서버 없이 차량 안에서 모두 처리합니다.

## 현재 상태

| 단계 | 내용 | 상태 |
|---|---|---|
| M0 | 프로젝트 생성, 빈 화면 | ✅ |
| M1-1 | FluidSynth 연결, SF3 사운드폰트로 오프라인 렌더 | ✅ (에뮬레이터 확인) |
| M1-2~4 | 주행 규제 중 동작, 오디오 포커스, 차량 데이터 수신 | 예정 |
| M2 | 엔진 포팅 (`drive_session.py`, 작곡 엔진 v3 → Kotlin) | 예정 |
| M3 화면 | 홈·도착·앨범·데모·설정(라이선스) 화면을 Figma 시안대로 구현 (예시 데이터) | 🟡 화면만 (재생·저장 미연결) |

## 동작 방식

```
주행 시작 → 차속/기어/깜빡이 샘플링 (화면·소리 없이 기록만)
도착(기어 P + 0km/h) → 2분 미만이면 폐기
            → 마디 분할 (기본 40초 / 데모 5초 = 1마디)
            → 장르 규칙으로 악보 생성
            → FluidSynth 오프라인 렌더 → 마스터링 → 오디오 파일
            → 도착 화면에서 공개·재생 → 오늘의 앨범에 추가
```

| 장르 | BPM |
|---|---|
| 미국 팝 | 128 |
| 팝 발라드 | 72 |
| 동요 | 112 |
| 트로트 | 134 |
| 댄스 | 140 |

## 심사용 데모 사용법

> 데모용 기능이며 실제 서비스에서는 제외하거나 축소할 수 있습니다.

(M3 이후 작성)

- '가상 주행 샘플 들어보기': 장르별 A/B 완성 트랙 10곡 (5장르 × 부드러운/급한 운전)
- 가상 주행으로 지금 곡 만들기
- 기본 모드 / 데모 모드 전환

## 테스트 환경

- Pleos Connect 에뮬레이터 (Android Automotive)
- 차량 신호 조작: Pleos Connect Virtual Console (`adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`)

## 빌드

- Android Studio, AGP 9.4.1 / Gradle 9.6.0 / Kotlin 2.2.10
- compileSdk·targetSdk 37, minSdk 28
- Pleos SDK: `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5`

- NDK 28.2, CMake 3.22.1: SDK 라이선스에 동의돼 있으면 첫 빌드 때 Gradle이 자동으로 설치합니다.
- FluidSynth 2.6.2: 첫 빌드 때 `fetchFluidSynth` 작업이 [공식 안드로이드 빌드](https://github.com/FluidSynth/fluidsynth/releases/tag/v2.6.2)(19MB)를 받아 SHA-256을 확인하고, 필요한 `.so`와 헤더만 `app/build/fluidsynth/`에 풉니다. 저장소에는 넣지 않습니다.
- ABI: `arm64-v8a`(실차), `x86_64`(Pleos Connect 에뮬레이터)

```bash
./gradlew :app:assembleDebug        # 개발용
./gradlew :app:assembleRelease      # 제출용 (디버그 키 서명)
./gradlew :app:testDebugUnitTest    # 단위 테스트
```

**소리 엔진 확인 (디버그 빌드)**: 홈 화면의 '소리 엔진 테스트' 버튼, 또는

```bash
adb shell am start -n com.kumhoht.todaysost/.ui.MainActivity --ez synthTest true
adb logcat -s SynthSmoke OstSynth FluidSynth
```

피아노·베이스·드럼(GeneralUser GS)과 스트링·브라스·코러스(FluidR3)를 렌더해 구간별·악기별 음량을 보여 주고 재생합니다.

## 코드 구조 (계획)

```
app/src/main/java/com/kumhoht/todaysost/
  core/model/        DriveSample, DriveSession, Track, DayAlbum, 장르·마디 규칙
  recorder/          차량 데이터 수집 Foreground Service + 세션 기록
  engine/session/    drive_session.py 포팅: 기본/데모 모드, 마디 분할
  engine/composer/   작곡 엔진 (Python v1~v3 → Kotlin), 주행 로그 → 마디 → 악보
  engine/synth/      FluidSynth JNI 래퍼 + 오프라인 렌더 + 마스터링 (NDK, CMake)
  data/              Room, 파일 저장소, 하루/월간 앨범 집계
  ui/                홈, 도착, 앨범('차곡차곡'), 데모, 설정
app/src/main/assets/
  soundfonts/        GeneralUser GS, FluidR3_GM (SF3 압축본, 아래 '사운드폰트' 참고)
  demo_tracks/       출품용 샘플 10트랙
```

## 사운드폰트

SF3 형식(샘플을 Ogg Vorbis로 압축한 사운드폰트)을 씁니다. FluidSynth 안드로이드 빌드가 직접 읽습니다.
원본 SF2 두 개를 그대로 쓰면 66MB인데, 압축 후 **15MB**입니다.

| 파일 | 악기 | 크기 | 만든 방법 |
|---|---|---|---|
| `GeneralUser-GS.sf3` | 피아노·기타·베이스·드럼·신스·오르간·색소폰·리코더·오르골 | 8.7MB | [GeneralUser GS v2.0.3](https://github.com/mrbumpy409/GeneralUser-GS) 전체를 SF3로 압축 (원본 31MB) |
| `FluidR3_GM_ensemble.sf3` | 스트링·코러스·브라스 (GM 40~45, 48~54, 56~63) | 6.7MB | FluidR3_GM(원본 141MB)에서 위 프리셋만 뽑아 SF3로 압축 |

- 악기와 프리셋 파라미터는 바꾸지 않고, 샘플만 Vorbis 품질 6(높은 편)으로 압축합니다.
- 도구가 변환 후 모든 샘플을 다시 풀어서 원본 길이와 루프 끝이 복원되는지 검사합니다.
- 인코더가 샘플 끝을 블록 단위로 반올림하므로 샘플마다 뒤에 무음 1024프레임을 덧붙입니다.

**다시 만들기** (악기 목록을 바꿀 때. FFmpeg(libvorbis 포함)가 필요하며 개발 PC에서만 씁니다)

1. 원본 받기
   - FluidR3_GM: Debian 패키지 [`fluid-soundfont-gm_3.1-6_all.deb`](https://deb.debian.org/debian/pool/main/f/fluid-soundfont/)를 풀면 `usr/share/sounds/sf2/FluidR3_GM.sf2`
   - GeneralUser GS: 위 저장소의 `GeneralUser-GS.sf2`
2. 실행:

```bash
./gradlew :tools:sf2subset:run --args="list <FluidR3_GM.sf2 경로>"
./gradlew :tools:sf2subset:run --args="sf3 <FluidR3_GM.sf2 경로> app/src/main/assets/soundfonts/FluidR3_GM_ensemble.sf3 --presets 0:40-45,0:48-54,0:56-63 --ffmpeg <ffmpeg.exe 경로>"
./gradlew :tools:sf2subset:run --args="sf3 <GeneralUser-GS.sf2 경로> app/src/main/assets/soundfonts/GeneralUser-GS.sf3 --ffmpeg <ffmpeg.exe 경로>"
```

## 오픈소스 라이선스

설정 > 오픈소스 라이선스 (주차 중에만 표시). 전문은 `app/src/main/assets/licenses/`.

| 구성 요소 | 라이선스 |
|---|---|
| FluidSynth | LGPL-2.1 (동적 링크 .so) |
| libsndfile (FluidSynth 의존성, SF3 읽기) | LGPL-2.1 (동적 링크 .so) |
| Oboe (FluidSynth 의존성) | Apache-2.0 |
| libogg, libvorbis, FLAC, Opus (libsndfile 의존성) | BSD-3-Clause |
| GeneralUser GS | GeneralUser GS License v2.0 |
| FluidR3_GM | MIT (Copyright 2000-2002, 2008 Frank Wen, 2008 Toby Smithe) |

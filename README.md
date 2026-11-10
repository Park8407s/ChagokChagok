# 차곡차곡 (ChagokChagok)

> 이전 이름: 오늘의 OST (Today's OST). 하루 동안 모인 트랙은 지금도 '오늘의 OST' 앨범이라고 부릅니다.

차속, 가감속, 깜빡이, 정차, 과속 같은 **주행 데이터를 규칙 기반 작곡 엔진으로 연주**해서 운전할 때마다 세상에 하나뿐인 트랙을 만드는 Pleos Connect 차량용 앱입니다.
도착하면 오늘의 트랙을 들려주고, 하루 동안 만든 트랙을 모아 '오늘의 OST' 앨범으로 남깁니다.

> **같은 길이라도 운전 습관에 따라 곡이 달라집니다.**

생성형 AI를 쓰지 않아 곡당 비용이 없고, 서버 없이 차량 안에서 모두 처리합니다.

## APK 파일

- 위치: [`apk/드라이브테일_차곡차곡_release.apk`](apk/드라이브테일_차곡차곡_release.apk) (약 54MB, 2026-11-10 노션 반영 버전)
- release 빌드 (공모전 제출용으로 디버그 키 서명), ABI `arm64-v8a`(실차) · `x86_64`(에뮬레이터)
- 실행 확인: Pleos Connect 에뮬레이터 v2.1.0 에서 설치 → 실행 → 데모 곡 생성·재생 (2026-11-06)

```bash
adb install -r -g "apk/드라이브테일_차곡차곡_release.apk"
```

`-g` 는 차량 권한(차속 등)을 설치할 때 한 번에 허용합니다. `-g` 없이 설치했다면 처음 실행할 때 차속 권한을 '허용'해 주세요.
하단 독(앱 목록)에서 **차곡차곡**(음표 아이콘)를 누르면 실행됩니다.
release 빌드에는 디버그용 기능(adb SIM 명령, 소리 엔진 테스트 버튼)이 없습니다.

## 현재 상태

| 단계 | 내용 | 상태 |
|---|---|---|
| M0 | 프로젝트 생성, 빈 화면 | ✅ |
| M1-1 | FluidSynth 연결, SF3 사운드폰트로 오프라인 렌더 | ✅ (에뮬레이터 확인) |
| M1-2~4 | 주행 규제 중 동작, 오디오 포커스, 차량 데이터 수신 | ✅ 에뮬레이터 확인 (아래 'M1 확인 결과') |
| 주행 기록 | Foreground Service 기록 → 도착 시 작곡·저장(Room)·재생 → 도착 화면 | ✅ Virtual Console 실제 신호로 확인 |
| M2 | 엔진 포팅 (`drive_session.py`, 작곡 엔진 v3 + 동요는 v2 → Kotlin), 렌더·마스터링·재생 연결 | ✅ Python 원본과 악보 비트 단위 일치 (테스트 12개) |
| M3 화면 | 홈·도착·앨범·데모·설정 화면 (Figma 시안 기준), 화면 이동·뒤로 가기, 재생 상태 표시, 최소 운행 시간 미만 안내, 데이터 초기화·앱 끄기 | ✅ 공유(받을 앱이 있을 때), 월간 OST 이어 듣기, 차량 미디어 세션 연동 포함 |

## M1 확인 결과 (Pleos Connect 에뮬레이터 v2.1.0, 2026-11-06)

| 항목 | 결과 |
|---|---|
| 차속 수신 | ✅ AAOS `PERF_VEHICLE_SPEED` (ACCEL 을 누르면 0 → 72km/h 값이 들어옴) |
| 기어 수신 | ✅ AAOS `GEAR_SELECTION` (P↔D) |
| 깜빡이 수신 | ✅ Pleos `getTurnSignal()` (`pleos.car.permission.CAR_LIGHTS`), Blink left/right/Off |
| 제한속도 | ⚠ NaviHelper 연결은 되지만 에뮬레이터는 항상 0 (붕붕냥과 같은 GPS 한계) → 과속 규칙은 데모 시나리오로 시연 |
| 주행 규제 중 기록 | ✅ 기어 D 에서 `uxRestricted=true`, 앱 화면은 가려지고 Foreground Service 는 계속 1초 기록 |
| 주행 중 백그라운드 재생 | ✅ 규제 중에도 MediaPlayer 가 `state:started` 로 계속 재생됨 → 실시간 재생을 확장 단계에서 검토할 수 있음 (1차는 주행 중 소리 없음 유지) |
| 내비 안내와 겹칠 때 | ✅ 내비 안내(`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, GAIN_TRANSIENT_MAY_DUCK)는 별도 출력(`bus1_navigation_out`)으로 동시에 나오고, 음악은 포커스를 잃지 않음. 에뮬레이터는 HAL 더킹을 쓰지 않아 음악 음량은 그대로 → **실차에서 더킹 확인 필요** |
| 도착 → 곡 | ✅ 143초 주행 → P → 8마디(기본 모드) → 15초 곡 저장·자동 재생·도착 화면 |

디버그 빌드 확인용 명령 (Virtual Console 없이):

```bash
adb shell am broadcast -a com.kumhoht.chagokchagok.SIM --es gear D --ef speed 60
adb shell am broadcast -a com.kumhoht.chagokchagok.SIM --ez navVoice true
adb shell am broadcast -a com.kumhoht.chagokchagok.SIM --ez playDemo true
adb shell am broadcast -a com.kumhoht.chagokchagok.SIM --ez clear true
adb logcat -s Telemetry DriveService TrackMaker OstPlayer
```

## 동작 방식

```
주행 시작 → 차속/기어/깜빡이 샘플링 (화면·소리 없이 기록만)
도착(기어 P + 0km/h) → 30초 미만이면 폐기
            → 마디 분할 (기본 40초 / 데모 5초 = 1마디)
            → 장르 규칙으로 악보 생성
            → FluidSynth 오프라인 렌더 → 마스터링 → 오디오 파일
            → 도착 화면에서 공개·재생 → 오늘의 앨범에 추가
```

| 장르 | BPM |
|---|---|
| 팝 | 128 |
| 발라드 | 72 |
| 동요 | 112 |
| 트로트 | 134 |
| 댄스 | 140 |

곡 길이 = 마디 수 × 4박 × 60 ÷ BPM (17분 출근길 30마디: 팝 56초, 발라드 1분 40초).
엔진 내부 장르 키는 예전 이름(`uspop`)이 남아 있지만 화면과 파일명은 `pop`(팝)·`ballad`(발라드)입니다.

마스터링은 작곡 엔진 v3 `make_final.py`의 수치를 따릅니다 (`engine/synth/Mastering.kt`).

| 단계 | 값 |
|---|---|
| 에코 | 60ms, 0.12 |
| 압축 | -18dB 이상 3:1, 어택 10ms·릴리즈 150ms |
| 음량 맞춤 | -14 LUFS (ITU-R BS.1770) |
| 리미터 | 최대 -1.5 dBFS |
| 끝 무음 제거 | 곡 끝 1.2초 전부터 1.5초 페이드아웃, 곡 끝 + 0.3초에서 자름 |

## 사용 방법

**화면 이동**: 맨 위 오른쪽 `홈 · (방금 곡) · 앨범 · 데모 · 설정` 버튼, 왼쪽 `‹ 이전` 버튼 또는 시스템 뒤로 가기.
주행 중(기어 D·R)에는 플랫폼이 앱 화면을 가리고, 주차하면 다시 보입니다.

| 화면 | 할 수 있는 것 |
|---|---|
| 홈 | 이번 운전의 장르 고르기 (저장됨), 어제의 OST 다시 듣기, 심사용 데모 입구 |
| 도착 | 운전이 끝나면 자동으로 열림. 곡 듣기·멈추기, **공유**(안드로이드 공유 창, 차량에 받을 앱이 없으면 안내), 곡 해설(타임라인·이벤트·마지막 화음) |
| 앨범 | 날짜별·트랙별 목록. 커버나 트랙 이름을 누르면 그 곡의 도착 화면. **○월의 OST 듣기**: 이번 달 트랙을 만든 순서대로 이어 재생 (트랙이 없으면 샘플 10곡), 다시 누르면 멈춤 |
| 데모 | 가상 주행 샘플 (아래 '심사용 데모') |
| 설정 | 곡 만들기(기본/데모 모드), 개인정보(데이터 초기화), 오픈소스 라이선스, 앱 정보(앱 끄기) |

**차량 미디어 연동**: 재생 중인 곡은 미디어 세션으로 차량에 알려집니다 (`OstMediaService`). 핸들·미디어 버튼으로 재생/일시정지/다음 곡/멈춤을 할 수 있고, 다음 곡은 월간 OST 이어 듣기에서 동작합니다.

- **30초 미만 운행은 곡을 만들지 않습니다** (인수인계 문서의 기획값 2분에서 출품 기준 30초로 변경. 값은 `TrackRules.MIN_DRIVE_SECONDS` 한 곳). 홈 안내 카드에 적혀 있고, 짧게 운전하고 도착하면 홈에 "N초 운전했어요…" 안내가 뜹니다.
- 재생 버튼은 상태를 표시합니다: 멈춤 `▶ 다시 듣기` / 곡 만드는 중 `준비 중…` / 재생 중 노란 `⏸ 멈추기`.
- **데이터 초기화** (설정 → 개인정보): 트랙·곡 파일·장르·마디 모드 설정을 지웁니다. 주차 중에만, 확인 창을 거쳐 실행됩니다.
- **앱 끄기** (설정 → 앱 정보): 기록 서비스와 재생을 멈추고 앱을 닫습니다. 앱을 다시 열거나 다음 시동 때 다시 켜집니다.
- 저장된 트랙이 없으면 홈·앨범은 시안의 예시 데이터를 보여 줍니다.

## 심사용 데모 사용법

> 데모용 기능이며 실제 서비스에서는 제외하거나 축소할 수 있습니다.

**운전 없이 듣기 (데모 화면)**
1. 위쪽 **데모** 버튼.
2. 장르별 **A 부드럽게 / B 급하게** 버튼: 같은 출근길(15.0km · 17분 · 30마디)을 다르게 운전한 기록으로 만든 **출품용 완성 트랙 10곡**(앱에 내장, 작곡 엔진 v3 `make_final.py` 결과)을 바로 재생합니다. 다시 누르면 멈춥니다.
3. 위쪽 A/B 카드를 고르고 **가상 주행으로 지금 만들기** → 앱의 작곡 엔진이 그 자리에서 새로 만들고(약 3초) 도착 화면에서 재생합니다.
4. 같은 장르의 A와 B를 비교해 들으면 "같은 길이라도 운전 습관에 따라 곡이 달라진다"를 확인할 수 있습니다 (B 는 급가속·급제동·과속이 소리와 커버의 노란 점으로 남음).

**Virtual Console 로 실제 운전하기**
1. 설정 → 곡 만들기 → **데모 모드**(주행 5초 = 1마디). 기본 모드(40초 = 1마디)는 짧은 시연에서 곡이 매우 짧아집니다 (30초 운전 → 약 9초, 2분 운전 → 약 15초).
2. 홈에서 장르를 고릅니다.
3. Virtual Console: Emulation Controls → Gear Selection **D** → **ACCEL** 을 1~2초씩 눌러 출발, **BRAKE** 로 정차, 깜빡이는 `turn` 검색 → Blink left/right.
4. **30초 넘게** 운전한 뒤 0km/h 로 세우고 Gear **P** → 몇 초 안에 도착 화면이 열리고 곡이 재생됩니다. 앨범에도 오늘 날짜로 추가됩니다.

- 에뮬레이터는 제한속도가 항상 0이라 실제 운전으로는 과속이 잡히지 않습니다. 과속 효과는 데모의 **B 급하게** 로 들어 보세요.
- PT 시연용 '최종 데모 모드 예시' 2곡(에뮬레이터 3분 조작)은 앱에 넣지 않았습니다. 시연에서는 Virtual Console 로 직접 운전해 만듭니다.

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

**소리 엔진 확인 (디버그 빌드)**: 설정 → 앱 정보의 '소리 엔진 테스트' 버튼, 또는

```bash
adb shell am start -n com.kumhoht.chagokchagok/.ui.MainActivity --ez synthTest true
adb logcat -s SynthSmoke OstSynth FluidSynth
```

피아노·베이스·드럼(GeneralUser GS)과 스트링·브라스·코러스(FluidR3)를 렌더해 구간별·악기별 음량을 보여 주고 재생합니다.

## 코드 구조

```
app/src/main/java/com/kumhoht/todaysost/
  OstApp.kt          앱 전체 객체 (DB·차량 신호·재생기·설정), 디버그 SIM 브로드캐스트
  core/model/        장르·BPM, 마디 모드(기본 40초/데모 5초), 최소 운행 시간 30초
  vehicle/           Telemetry: 차속·기어(AAOS), 브레이크·깜빡이(Pleos), 제한속도(NaviHelper), 주행 규제
  recorder/          DriveService: 1초 기록 Foreground Service, 도착 시 작곡·저장·재생, 앱 끄기, BootReceiver
  engine/session/    DriveSession(drive_session.py 포팅: 마디 나누기), DriveRecorder(운행 시작·끝·최소 시간 판정)
  engine/composer/   ComposerV3(drive_music_v3.py 포팅), KidsComposerV2(동요, v2), PyRandom(Python 난수), 시연 시나리오
  engine/synth/      FluidSynth JNI(OfflineRenderer), ScoreRenderer(악보 → 이벤트), Mastering
  engine/TrackMaker  마디 → 작곡 → 렌더 → 마스터링 → WAV
  data/              Room(TrackEntity): 트랙·하루 앨범
  ui/                MainActivity(화면 이동·뒤로 가기), OstPlayer(재생·오디오 포커스)
  ui/screens/        홈·도착·앨범·데모·설정
  ui/components/     앨범 커버 생성, 타임라인, 상단 줄, 안내·확인 창
app/src/main/cpp/    ostsynth_jni.cpp (FluidSynth 동적 링크)
app/src/main/assets/
  soundfonts/        GeneralUser GS, FluidR3_GM (SF3 압축본, 아래 '사운드폰트' 참고)
  demo_tracks/       출품용 샘플 10곡 (5장르 × A 부드러운 / B 급한 운전, MP3 192kbps, 약 16MB)
  licenses/          라이선스 전문
tools/sf2subset/     사운드폰트 프리셋 추출·SF3 압축 도구 (개발 PC용)
tools/parity/        원본 Python 엔진 악보 추출 (ParityTest 기준 데이터)
```

테스트: `./gradlew :app:testDebugUnitTest` (21개). `ParityTest` 는 Kotlin 엔진이 원본 Python 엔진과 같은 악보를 내는지 음 단위로 비교합니다.

## 사운드폰트

SF3 형식(샘플을 Ogg Vorbis로 압축한 사운드폰트)을 씁니다. FluidSynth 안드로이드 빌드가 직접 읽습니다.
원본 SF2 두 개를 그대로 쓰면 66MB인데, 압축 후 **16MB**입니다.

| 파일 | 악기 | 크기 | 만든 방법 |
|---|---|---|---|
| `GeneralUser-GS.sf3` | 피아노·기타·베이스·드럼·신스·오르간·색소폰·리코더·오르골 | 8.7MB | [GeneralUser GS v2.0.3](https://github.com/mrbumpy409/GeneralUser-GS) 전체를 SF3로 압축 (원본 31MB) |
| `FluidR3_GM_ensemble.sf3` | 스트링·하프·팀파니·코러스·브라스 (GM 40~54, 56~63, 엔진 v3와 같은 배정) | 7.1MB | FluidR3_GM(원본 141MB)에서 위 프리셋만 뽑아 SF3로 압축 |

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
./gradlew :tools:sf2subset:run --args="sf3 <FluidR3_GM.sf2 경로> app/src/main/assets/soundfonts/FluidR3_GM_ensemble.sf3 --presets 0:40-54,0:56-63 --ffmpeg <ffmpeg.exe 경로>"
./gradlew :tools:sf2subset:run --args="sf3 <GeneralUser-GS.sf2 경로> app/src/main/assets/soundfonts/GeneralUser-GS.sf3 --ffmpeg <ffmpeg.exe 경로>"
```

## 오픈소스 라이선스

설정 > 오픈소스 라이선스 (주차 중에만 표시). 전문은 `app/src/main/assets/licenses/`: FluidSynth와 함께 들어 있는 라이브러리 원문 전체는 `FluidSynth_and_libraries.txt`(FluidSynth 카드의 '전문 보기').

| 구성 요소 | 라이선스 |
|---|---|
| FluidSynth | LGPL-2.1 (동적 링크 .so) |
| libsndfile (FluidSynth 의존성, SF3 읽기) | LGPL-2.1 (동적 링크 .so) |
| Oboe (FluidSynth 의존성) | Apache-2.0 |
| libogg, libvorbis, FLAC, Opus (libsndfile 의존성) | BSD-3-Clause |
| GeneralUser GS | GeneralUser GS License v2.0 |
| FluidR3_GM | MIT (Copyright 2000-2002, 2008 Frank Wen, 2008 Toby Smithe) |

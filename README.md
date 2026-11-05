# 오늘의 OST (Today's OST)

차속, 가감속, 깜빡이, 정차, 과속 같은 **주행 데이터를 규칙 기반 작곡 엔진으로 연주**해서 운전할 때마다 세상에 하나뿐인 트랙을 만드는 Pleos Connect 차량용 앱입니다.
도착하면 오늘의 트랙을 들려주고, 하루 동안 만든 트랙을 모아 '오늘의 OST' 앨범으로 남깁니다.

> **같은 길이라도 운전 습관에 따라 곡이 달라집니다.**

생성형 AI를 쓰지 않아 곡당 비용이 없고, 서버 없이 차량 안에서 모두 처리합니다.

## 현재 상태

| 단계 | 내용 | 상태 |
|---|---|---|
| M0 | 프로젝트 생성, 빈 화면 | ✅ |
| M1 | 기술 리스크 검증 (FluidSynth NDK, 주행 규제, 오디오 포커스, 차량 데이터) | 예정 |
| M2 | 엔진 포팅 (`drive_session.py`, 작곡 엔진 v3 → Kotlin) | 예정 |
| M3~ | 도착 화면, 하루 앨범, 월간 OST, 가상 주행 샘플, 설정·라이선스 | 예정 |

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

```bash
./gradlew :app:assembleDebug        # 개발용
./gradlew :app:assembleRelease      # 제출용 (디버그 키 서명)
./gradlew :app:testDebugUnitTest    # 단위 테스트
```

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
  soundfonts/        GeneralUser GS, FluidR3_GM (저장소 제외, 아래 '사운드폰트' 참고)
  demo_tracks/       출품용 샘플 10트랙
```

## 사운드폰트

> **용량이 커서 저장소에는 올리지 않습니다.** 빌드 전에 아래 두 파일을 `app/src/main/assets/soundfonts/`에 직접 넣어 주세요.
> (파일이 없어도 빌드는 되지만 곡이 렌더링되지 않습니다.)

| 파일 | 악기 | 크기 | 받는 법 |
|---|---|---|---|
| `GeneralUser-GS.sf2` | 피아노·기타·베이스·드럼·신스·오르간·색소폰·리코더·오르골 | 31MB | [GeneralUser GS v2.0.3](https://github.com/mrbumpy409/GeneralUser-GS) 저장소의 `GeneralUser-GS.sf2` 그대로 |
| `FluidR3_GM_ensemble.sf2` | 스트링·코러스·브라스 (GM 40~45, 48~54, 56~63) | 35MB | FluidR3_GM 원본(141MB)에서 아래 도구로 프리셋만 추출 |

추출본은 샘플과 파라미터를 바꾸지 않으며, 도구가 모든 샘플이 원본과 바이트 단위로 같은지 검사합니다.

**FluidR3_GM 추출본 만들기**

1. 원본 받기: Debian 패키지 [`fluid-soundfont-gm_3.1-6_all.deb`](https://deb.debian.org/debian/pool/main/f/fluid-soundfont/)를 풀면 `usr/share/sounds/sf2/FluidR3_GM.sf2`
2. 실행:

```bash
./gradlew :tools:sf2subset:run --args="list <FluidR3_GM.sf2 경로>"
./gradlew :tools:sf2subset:run --args="extract <FluidR3_GM.sf2 경로> app/src/main/assets/soundfonts/FluidR3_GM_ensemble.sf2 0:40-45,0:48-54,0:56-63"
```

## 오픈소스 라이선스

설정 > 오픈소스 라이선스 (주차 중에만 표시). 전문은 `app/src/main/assets/licenses/`.

| 구성 요소 | 라이선스 |
|---|---|
| FluidSynth | LGPL-2.1 (동적 링크 .so) |
| GeneralUser GS | GeneralUser GS License v2.0 |
| FluidR3_GM | MIT (Copyright 2000-2002, 2008 Frank Wen, 2008 Toby Smithe) |

"""
원본 Python 작곡 엔진의 악보(음 목록)를 텍스트로 뽑는다. Kotlin 포팅과 비교하는 기준 데이터.

사용법 (Python 3 만 있으면 됨. pretty_midi·FluidSynth·ffmpeg 설치 불필요):
    python -I tools/parity/dump_events.py <원본 스크립트 폴더> app/src/test/resources/parity

- pretty_midi 대신 음을 기록만 하는 가짜 모듈을 끼워 넣고, fluidsynth·ffmpeg 호출(subprocess.run)은 막는다.
- 결과 파일 한 줄 형식 (float 은 repr 그대로 → Kotlin 에서 비트 단위로 비교):
    N <트랙> <프로그램> <드럼0/1> <pitch> <velocity> <start> <end>
    B <트랙> <value> <time>
    S <마디 번호> <speed> <ev> <sig> <over>     (drive_session 결과)
"""
import os
import subprocess
import sys
import types

src, out_dir = os.path.abspath(sys.argv[1]), os.path.abspath(sys.argv[2])
os.makedirs(out_dir, exist_ok=True)

# ---------------- 가짜 pretty_midi ----------------
pm = types.ModuleType('pretty_midi')
CAPTURED = []


class Note:
    def __init__(self, velocity, pitch, start, end):
        self.velocity, self.pitch, self.start, self.end = velocity, pitch, start, end


class PitchBend:
    def __init__(self, pitch, time):
        self.pitch, self.time = pitch, time


class Instrument:
    def __init__(self, program, is_drum=False, name=''):
        self.program, self.is_drum, self.name = program, is_drum, name
        self.notes, self.pitch_bends = [], []


class PrettyMIDI:
    def __init__(self, initial_tempo=120.0):
        self.instruments = []

    def write(self, path):
        CAPTURED.append(self)


pm.Note, pm.PitchBend, pm.Instrument, pm.PrettyMIDI = Note, PitchBend, Instrument, PrettyMIDI
sys.modules['pretty_midi'] = pm
subprocess.run = lambda *a, **k: None   # fluidsynth·ffmpeg 실행 안 함

sys.path.insert(0, src)
import drive_music_v2 as v2      # noqa: E402
import drive_music_v3 as v3      # noqa: E402
from drive_music import SMOOTH, ROUGH   # noqa: E402
import drive_session             # noqa: E402


def dump(build, genre, drive, name):
    CAPTURED.clear()
    build(genre, drive, name)
    midi = CAPTURED[-1]
    with open(os.path.join(out_dir, name + '.txt'), 'w', encoding='utf-8') as f:
        for inst in midi.instruments:
            for n in inst.notes:
                f.write(f'N {inst.name} {inst.program} {int(inst.is_drum)} {n.pitch} {n.velocity} {n.start!r} {n.end!r}\n')
            for b in inst.pitch_bends:
                f.write(f'B {inst.name} {b.pitch} {b.time!r}\n')
    print(name, sum(len(i.notes) for i in midi.instruments), 'notes')


for genre in ('uspop', 'ballad', 'trot', 'dance'):
    dump(v3.build, genre, SMOOTH, f'v3_{genre}_A')
    dump(v3.build, genre, ROUGH, f'v3_{genre}_B')
dump(v2.build, 'kids', SMOOTH, 'v2_kids_A')
dump(v2.build, 'kids', ROUGH, 'v2_kids_B')

# drive_session: 시연용 주행 기록 → 마디 (두 모드) + 그 마디로 만든 곡
log = drive_session.demo_log()
for mode in ('normal', 'demo'):
    bars, secs = drive_session.segment(log, mode)
    with open(os.path.join(out_dir, f'session_{mode}.txt'), 'w', encoding='utf-8') as f:
        f.write(f'T {secs}\n')
        for i, b in enumerate(bars):
            f.write(f"S {i} {b['speed']} {b['ev']} {b['sig']} {int(b['over'])}\n")
bars, _ = drive_session.segment(log, 'demo')
dump(v3.build, 'uspop', bars, 'v3_uspop_session_demo')
dump(v3.build, 'trot', bars, 'v3_trot_session_demo')

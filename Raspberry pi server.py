from flask import Flask, jsonify, request, send_from_directory
from picamera2 import Picamera2
from picamera2.encoders import H264Encoder
from picamera2.outputs import FileOutput
from datetime import datetime, timedelta
import os
import time
import threading
import subprocess
import requests

app = Flask(__name__)

# =========================
# 기본 설정
# =========================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
VIDEO_DIR = os.path.join(BASE_DIR, "video")
os.makedirs(VIDEO_DIR, exist_ok=True)

# AWS EC2 주소로 수정
AWS_URL = "AWS_URL"

# 로컬 테스트용 라즈베리파이 주소
# 앱은 이 주소를 쓰면 안 됨. 앱은 AWS_URL만 사용.
LOCAL_SERVER_URL = "LOCAL_SERVER_URL"

camera = Picamera2()
camera.configure(camera.create_video_configuration())

recording = False
current_file = None
recording_lock = threading.Lock()


# =========================
# 공통 함수
# =========================

def make_base_name(prefix):
    now = datetime.now().strftime("%Y%m%d_%H%M%S")
    return f"{prefix}_{now}"


def get_prefix_by_mode(mode):
    if mode == "mode1":
        return "pir"
    if mode == "mode2":
        return "app"
    if mode == "mode3":
        return "schedule"
    return ""


def start_recording(prefix):
    """
    녹화 시작.
    먼저 h264로 저장하고, 녹화 종료 후 mp4로 변환한다.
    """
    global recording, current_file

    with recording_lock:
        if recording:
            return False, "already recording"

        base_name = make_base_name(prefix)

        h264_path = os.path.join(VIDEO_DIR, base_name + ".h264")
        mp4_path = os.path.join(VIDEO_DIR, base_name + ".mp4")

        encoder = H264Encoder()
        output = FileOutput(h264_path)

        camera.start_recording(encoder, output)

        recording = True
        current_file = {
            "base_name": base_name,
            "h264_path": h264_path,
            "mp4_path": mp4_path
        }

        return True, base_name + ".mp4"


def stop_recording_and_upload(mode):
    """
    녹화 종료 → h264를 mp4로 변환 → AWS 업로드
    """
    global recording, current_file

    with recording_lock:
        if not recording:
            return False, "not recording"

        camera.stop_recording()

        info = current_file
        recording = False
        current_file = None

    h264_path = info["h264_path"]
    mp4_path = info["mp4_path"]

    if not os.path.exists(h264_path):
        print(f"[ERROR] h264 파일 없음: {h264_path}")
        return False, "h264 file not found"

    try:
        print("[FFMPEG] mp4 변환 시작")

        subprocess.run([
            "ffmpeg",
            "-y",
            "-framerate", "30",
            "-i", h264_path,
            "-c", "copy",
            "-movflags", "+faststart",
            mp4_path
        ], check=True)

        print(f"[FFMPEG] mp4 변환 완료: {mp4_path}")

        if os.path.exists(h264_path):
            os.remove(h264_path)

    except Exception as e:
        print(f"[ERROR] ffmpeg 변환 실패: {e}")
        return False, str(e)

    upload_ok = upload_to_aws(mode, mp4_path)

    if upload_ok:
        print(f"[UPLOAD] AWS 업로드 완료: {mp4_path}")
    else:
        print(f"[UPLOAD] AWS 업로드 실패: {mp4_path}")

    return True, mp4_path


def upload_to_aws(mode, mp4_path):
    """
    완성된 mp4를 AWS로 업로드.
    """
    try:
        upload_url = f"{AWS_URL}/upload/{mode}"

        with open(mp4_path, "rb") as f:
            files = {
                "video": (os.path.basename(mp4_path), f, "video/mp4")
            }
            response = requests.post(upload_url, files=files, timeout=60)

        print("[AWS 응답 코드]", response.status_code)
        print("[AWS 응답]", response.text)

        return response.status_code == 200

    except Exception as e:
        print("[ERROR] AWS 업로드 오류: {e}")
        return False


def capture_fixed(mode, prefix, duration):
    """
    duration초 동안 촬영 후 AWS 업로드.
    """
    if recording:
        print("[CAPTURE] 이미 녹화 중이라 무시")
        return

    duration = int(duration)

    print(f"[CAPTURE] {mode} {duration}초 녹화 시작")

    ok, result = start_recording(prefix)

    if not ok:
        print(f"[CAPTURE] 녹화 시작 실패: {result}")
        return

    print(f"[CAPTURE] 저장 예정 파일: {result}")

    time.sleep(duration)

    ok, saved = stop_recording_and_upload(mode)

    if ok:
        print(f"[CAPTURE] 녹화/업로드 완료: {saved}")
    else:
        print(f"[CAPTURE] 녹화 종료 실패: {saved}")


# =========================
# 로컬 테스트용 영상 목록
# 앱은 이걸 쓰지 않고 AWS를 봐야 함
# =========================

def get_local_videos_by_mode(mode):
    prefix = get_prefix_by_mode(mode)

    files = sorted(
        [
            f for f in os.listdir(VIDEO_DIR)
            if f.endswith(".mp4") and f.startswith(prefix + "_")
        ],
        reverse=True
    )

    videos = []

    for filename in files:
        videos.append({
            "filename": filename,
            "url": f"{LOCAL_SERVER_URL}/{mode}/video/{filename}"
        })

    return jsonify({
        "status": "success",
        "videos": videos
    })


def send_local_video(filename):
    response = send_from_directory(VIDEO_DIR, filename, mimetype="video/mp4")
    response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate, max-age=0"
    response.headers["Pragma"] = "no-cache"
    response.headers["Expires"] = "0"
    response.headers["Accept-Ranges"] = "bytes"
    return response


# =========================
# Mode3 예약 처리
# =========================

def parse_hhmm_to_datetime(time_text):
    hour, minute = map(int, time_text.split(":"))
    now = datetime.now()

    return now.replace(
        hour=hour,
        minute=minute,
        second=0,
        microsecond=0
    )


def schedule_by_delay(delay, duration):
    """
    delay초 뒤에 duration초 촬영.
    """
    delay = int(delay)
    duration = int(duration)

    print(f"[MODE3] {delay}초 뒤 {duration}초 촬영 예약")

    time.sleep(delay)

    capture_fixed("mode3", "schedule", duration)


def schedule_by_time_range(start_time, end_time, interval, duration):
    """
    startTime부터 endTime까지 interval초 간격으로 duration초 촬영.

    예:
    startTime = "15:00"
    endTime = "16:00"
    interval = 60
    duration = 10
    """
    interval = int(interval)
    duration = int(duration)

    start_dt = parse_hhmm_to_datetime(start_time)
    end_dt = parse_hhmm_to_datetime(end_time)
    now = datetime.now()

    if end_dt <= start_dt:
        end_dt += timedelta(days=1)

    if now >= end_dt:
        start_dt += timedelta(days=1)
        end_dt += timedelta(days=1)

    if now < start_dt:
        wait_seconds = (start_dt - now).total_seconds()
        print(f"[MODE3] 시작 시간까지 {int(wait_seconds)}초 대기")
        time.sleep(wait_seconds)

    print("[MODE3] 예약 촬영 구간 시작")

    while datetime.now() < end_dt:
        capture_fixed("mode3", "schedule", duration)

        print(f"[MODE3] 다음 촬영까지 {interval}초 대기")
        time.sleep(interval)

    print("[MODE3] 예약 촬영 구간 종료")


def handle_mode3_schedule_command(command):
    """
    Mode3 예약 명령 처리.

    지원 1:
    {
        "delay": 60,
        "duration": 10
    }

    지원 2:
    {
        "startTime": "15:00",
        "endTime": "16:00",
        "interval": 60,
        "duration": 10
    }
    """
    duration = int(command.get("duration", 10))

    if "startTime" in command and "endTime" in command:
        start_time = command.get("startTime")
        end_time = command.get("endTime")
        interval = int(command.get("interval", 60))

        threading.Thread(
            target=schedule_by_time_range,
            args=(start_time, end_time, interval, duration),
            daemon=True
        ).start()

        return {
            "type": "time_range",
            "startTime": start_time,
            "endTime": end_time,
            "interval": interval,
            "duration": duration
        }

    delay = int(command.get("delay", 0))

    threading.Thread(
        target=schedule_by_delay,
        args=(delay, duration),
        daemon=True
    ).start()

    return {
        "type": "delay",
        "delay": delay,
        "duration": duration
    }


# =========================
# 로컬 API
# ESP32가 호출하는 API
# =========================

@app.route("/", methods=["GET"])
def home():
    return jsonify({
        "status": "success",
        "message": "Raspberry Pi Capture Server Running"
    })


@app.route("/record/mode1/pir", methods=["GET", "POST"])
def record_mode1_pir():
    print("[MODE1] ESP32 PIR 신호 수신")

    threading.Thread(
        target=capture_fixed,
        args=("mode1", "pir", 10),
        daemon=True
    ).start()

    return jsonify({
        "status": "success",
        "message": "mode1 capture started"
    })


# =========================
# 로컬 테스트용 Mode2 API
# 앱은 AWS /mode2/start, /mode2/stop을 호출해야 함
# =========================

@app.route("/mode2/start", methods=["POST"])
def local_mode2_start():
    print("[MODE2] 로컬 녹화 시작 요청")

    ok, result = start_recording("app")

    if ok:
        return jsonify({
            "status": "success",
            "message": "mode2 recording started",
            "filename": result
        })

    return jsonify({
        "status": "fail",
        "message": result
    }), 400


@app.route("/mode2/stop", methods=["POST"])
def local_mode2_stop():
    print("[MODE2] 로컬 녹화 중지 요청")

    ok, result = stop_recording_and_upload("mode2")

    if ok:
        return jsonify({
            "status": "success",
            "message": "mode2 recording stopped",
            "saved": result
        })

    return jsonify({
        "status": "fail",
        "message": result
    }), 400


# =========================
# 로컬 테스트용 Mode3 API
# 앱은 AWS /schedule/mode3를 호출해야 함
# =========================

@app.route("/schedule/mode3", methods=["POST"])
def local_schedule_mode3():
    data = request.get_json() or {}

    result = handle_mode3_schedule_command(data)

    return jsonify({
        "status": "success",
        "message": "local mode3 schedule registered",
        "schedule": result
    })


# =========================
# 로컬 테스트용 영상 목록
# 앱은 AWS에서 목록을 봐야 함
# =========================

@app.route("/mode1/videos", methods=["GET"])
def local_mode1_videos():
    return get_local_videos_by_mode("mode1")


@app.route("/mode2/videos", methods=["GET"])
def local_mode2_videos():
    return get_local_videos_by_mode("mode2")


@app.route("/mode3/videos", methods=["GET"])
def local_mode3_videos():
    return get_local_videos_by_mode("mode3")


@app.route("/mode1/video/<filename>", methods=["GET"])
def local_mode1_video_file(filename):
    return send_local_video(filename)


@app.route("/mode2/video/<filename>", methods=["GET"])
def local_mode2_video_file(filename):
    return send_local_video(filename)


@app.route("/mode3/video/<filename>", methods=["GET"])
def local_mode3_video_file(filename):
    return send_local_video(filename)


# =========================
# AWS 명령 가져오기
# 라즈베리파이가 주기적으로 AWS를 확인
# =========================

def poll_aws_commands():
    while True:
        try:
            response = requests.get(f"{AWS_URL}/commands/pop", timeout=10)
            data = response.json()

            if data.get("type") == "none":
                time.sleep(2)
                continue

            command = data.get("command", {})
            command_type = command.get("type")

            if command_type == "mode2_start":
                print("[COMMAND] mode2_start 수신")
                ok, result = start_recording("app")
                print("[MODE2]", result)

            elif command_type == "mode2_stop":
                print("[COMMAND] mode2_stop 수신")
                ok, result = stop_recording_and_upload("mode2")
                print("[MODE2]", result)

            elif command_type == "mode3_schedule":
                print("[COMMAND] mode3_schedule 수신")
                result = handle_mode3_schedule_command(command)
                print("[MODE3] 예약 등록:", result)

            else:
                print("[COMMAND] 알 수 없는 명령:", command)

        except Exception as e:
            print("[ERROR] AWS 명령 확인 실패:", e)

        time.sleep(2)


# =========================
# 실행
# =========================

if __name__ == "__main__":
    camera.start()

    threading.Thread(
        target=poll_aws_commands,
        daemon=True
    ).start()

    app.run(host="0.0.0.0", port=5000)

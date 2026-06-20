"""
faster-whisper 本地 HTTP API 服务
==================================
基于 faster-whisper 将音频文件转为文字，替换 DashScope ASR 的本地替代方案。

启动方式：
    python whisper_server.py --model base --device cpu --port 9090

支持的模型大小：tiny / base / small / medium / large-v3
设备选项：cpu / cuda

API 端点：
    POST /asr          - 音频文件转文字
    POST /asr/stream   - 流式转写 (SSE)
     GET /health       - 健康检查
"""

import argparse
import json
import logging
import os
import time
import uuid
from pathlib import Path
from typing import Optional

# 修复微软商店 Python SSL 证书问题（须在 import huggingface_hub 之前设置）
os.environ["HF_HUB_DISABLE_SSL_VERIFY"] = "1"
os.environ["CURL_CA_BUNDLE"] = ""
os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"

import uvicorn
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse

# 进一步修补 httpx SSL 验证（微软商店 Python 的证书问题）
import httpx
import huggingface_hub.utils._http as hf_http
hf_http.get_session = lambda: httpx.Client(verify=False, timeout=httpx.Timeout(30.0))

# ---------- faster-whisper 懒加载 ----------
_model = None
_model_name = None
_model_device = None
_model_compute = None

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("whisper-server")

app = FastAPI(title="Local Whisper ASR Server", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def get_model(model_name: str, device: str, compute_type: str):
    """获取或初始化 faster-whisper 模型（单例）"""
    global _model, _model_name, _model_device, _model_compute
    if _model is None or _model_name != model_name or _model_device != device or _model_compute != compute_type:
        from faster_whisper import WhisperModel

        log.info("加载 faster-whisper 模型: %s (device=%s, compute=%s)", model_name, device, compute_type)
        _model = WhisperModel(model_name, device=device, compute_type=compute_type)
        _model_name = model_name
        _model_device = device
        _model_compute = compute_type
        log.info("模型加载完成")
    return _model


def save_temp_audio(file_bytes: bytes, suffix: str = ".webm") -> str:
    """将上传的音频字节保存到临时文件"""
    temp_dir = Path(os.environ.get("TEMP", "/tmp"))
    temp_path = temp_dir / f"whisper_{uuid.uuid4().hex}{suffix}"
    with open(temp_path, "wb") as f:
        f.write(file_bytes)
    return str(temp_path)


def detect_language(text_segment) -> str:
    """从 faster-whisper 返回的 segment 推断语言"""
    if hasattr(text_segment, "language") and text_segment.language:
        lang = text_segment.language
        if lang == "zh":
            return "zh"
        return lang
    return "zh"  # 默认中文


# ====================== API 端点 ======================


@app.get("/health")
async def health():
    """健康检查"""
    return {
        "status": "ok",
        "model": _model_name or "未加载",
        "device": _model_device or "N/A",
    }


@app.post("/asr")
async def transcribe(
    audio: UploadFile = File(...),
    model: str = Form("base"),
    device: str = Form("cpu"),
    compute_type: str = Form("auto"),
    language: Optional[str] = Form(None),
):
    """
    音频文件转文字（非流式）

    参数:
        audio:      音频文件 (webm, wav, mp3, ogg 等)
        model:      模型大小 (tiny/base/small/medium/large-v3)
        device:     cpu 或 cuda
        language:   语言代码 (zh/en/ja 等)，留空自动检测

    返回:
        {"text": "识别文本", "segments": [...], "language": "zh", "elapsed": 1234}
    """
    start_time = time.time()
    log.info("===== Whisper ASR 请求 =====")

    try:
        audio_bytes = await audio.read()
        log.info("音频接收完成: %s, size=%dKB", audio.filename or "unknown", len(audio_bytes) / 1024)

        # 从 Content-Type 推断扩展名
        content_type = audio.content_type or ""
        ext_map = {
            "webm": ".webm", "opus": ".opus", "ogg": ".ogg",
            "wav": ".wav", "mp3": ".mp3", "mp4": ".mp4",
            "aac": ".aac", "amr": ".amr", "pcm": ".pcm",
        }
        ext = ".webm"
        for key, value in ext_map.items():
            if key in content_type:
                ext = value
                break

        temp_path = save_temp_audio(audio_bytes, ext)
        log.info("临时音频文件: %s", temp_path)

        # 加载模型
        whisper = get_model(model, device, compute_type)

        # 执行转写
        log.info("开始转写 (language=%s)...", language or "auto")
        segments, info = whisper.transcribe(temp_path, language=language, beam_size=5)

        # 收集结果
        full_text = ""
        segment_list = []
        detected_lang = info.language if info and info.language else "zh"

        for segment in segments:
            full_text += segment.text + " "
            segment_list.append({
                "start": segment.start,
                "end": segment.end,
                "text": segment.text.strip(),
            })

        full_text = full_text.strip()
        elapsed_ms = int((time.time() - start_time) * 1000)
        log.info("转写完成: text=\"%s...\", segments=%d, elapsed=%dms",
                 full_text[:50], len(segment_list), elapsed_ms)

        # 清理临时文件
        try:
            os.remove(temp_path)
        except OSError:
            pass

        return JSONResponse(content={
            "text": full_text,
            "segments": segment_list,
            "language": detected_lang,
            "duration": round(info.duration, 2) if info else 0,
            "elapsed": elapsed_ms,
            "status": "success",
        })

    except Exception as e:
        log.error("Whisper ASR 失败: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/asr/stream")
async def transcribe_stream(
    audio: UploadFile = File(...),
    model: str = Form("base"),
    device: str = Form("cpu"),
    compute_type: str = Form("auto"),
    language: Optional[str] = Form(None),
):
    """
    音频文件转文字（SSE 流式）

    返回 SSE 事件流，每识别出一个句子发送一次结果。
    """
    start_time = time.time()
    log.info("===== Whisper ASR 流式请求 =====")

    try:
        audio_bytes = await audio.read()
        log.info("音频接收完成: size=%dKB", len(audio_bytes) / 1024)

        content_type = audio.content_type or ""
        ext = ".webm"
        for key, value in {"webm": ".webm", "opus": ".opus", "wav": ".wav",
                           "mp3": ".mp3", "ogg": ".ogg"}.items():
            if key in content_type:
                ext = value
                break

        temp_path = save_temp_audio(audio_bytes, ext)
        whisper = get_model(model, device, compute_type)

    except Exception as e:
        log.error("Whisper 流式初始化失败: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

    async def event_generator():
        try:
            segments, info = whisper.transcribe(temp_path, language=language, beam_size=5)
            detected_lang = info.language if info and info.language else "zh"

            for segment in segments:
                text = segment.text.strip()
                if not text:
                    continue

                event_data = json.dumps({
                    "text": text,
                    "isFinal": True,
                    "start": segment.start,
                    "end": segment.end,
                }, ensure_ascii=False)
                yield f"event: transcript\ndata: {event_data}\n\n"

            elapsed_ms = int((time.time() - start_time) * 1000)
            yield f"event: complete\ndata: {json.dumps({'status': 'done', 'elapsed': elapsed_ms, 'language': detected_lang})}\n\n"

        except Exception as e:
            log.error("Whisper 流式转写失败: %s", e, exc_info=True)
            yield f"event: error\ndata: {json.dumps({'error': str(e)})}\n\n"
        finally:
            try:
                os.remove(temp_path)
            except OSError:
                pass

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


# ====================== 主入口 ======================

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="faster-whisper HTTP API 服务")
    parser.add_argument("--model", default="base", help="模型大小: tiny/base/small/medium/large-v3 (默认: base)")
    parser.add_argument("--device", default="cpu", help="设备: cpu 或 cuda (默认: cpu)")
    parser.add_argument("--compute", default="auto", help="计算类型: auto/int8/float16 (默认: auto)")
    parser.add_argument("--port", type=int, default=9090, help="服务端口 (默认: 9090)")
    parser.add_argument("--host", default="0.0.0.0", help="监听地址 (默认: 0.0.0.0)")
    args = parser.parse_args()

    # 启动时预加载模型
    log.info("正在预加载 faster-whisper 模型: %s (device=%s)...", args.model, args.device)
    get_model(args.model, args.device, args.compute)

    log.info("Whisper ASR 服务启动: http://%s:%d", args.host, args.port)
    log.info("API 端点:")
    log.info("  POST /asr        - 音频文件转文字")
    log.info("  POST /asr/stream - 流式转写 (SSE)")
    log.info("  GET  /health     - 健康检查")

    uvicorn.run(app, host=args.host, port=args.port, log_level="info")

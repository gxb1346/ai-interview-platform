"""
Edge-TTS 本地 HTTP API 服务
==========================
基于 edge-tts 将文字转为语音，替代付费的 DashScope CosyVoice TTS。

特点：
- 完全免费，无需 API Key
- 中文语音自然流畅（Azure 高质量神经网络 TTS）
- 支持多种音色、语速调节
- CPU 即可运行，低延迟（约 0.5~2 秒）
- 适合面试场景的沉稳男声

启动方式：
    python edge-tts-server.py --port 9091

API 端点：
    POST /tts    - 文字转语音
     GET /health - 健康检查
     GET /voices - 获取可用音色列表
"""

import argparse
import asyncio
import base64
import json
import logging
import os
import time
from typing import Optional

import edge_tts
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("edge-tts-server")

app = FastAPI(title="Local Edge-TTS Server", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 默认音色：云希（沉稳男声，适合技术面试场景）
# 可选的常用中文音色见 /voices 端点
DEFAULT_VOICE = "zh-CN-YunxiNeural"

# 常用中文音色列表
SUPPORTED_VOICES = {
    "zh-CN-XiaoxiaoNeural": "晓晓（女声，亲切自然）",
    "zh-CN-YunxiNeural": "云希（男声，沉稳专业）★推荐",
    "zh-CN-YunyangNeural": "云扬（男声，新闻播报）",
    "zh-CN-XiaochenNeural": "晓辰（女声，温暖知性）",
    "zh-CN-XiaohanNeural": "晓涵（女声，活泼开朗）",
    "zh-CN-XiaomoNeural": "晓墨（女声，温柔亲和）",
    "zh-CN-XiaoruiNeural": "晓睿（男声，年轻活力）",
    "zh-CN-XiaoshuangNeural": "晓双（女声，清爽干练）",
}


class TTSRequest(BaseModel):
    text: str
    voice: str = DEFAULT_VOICE
    rate: str = "+0%"       # 语速调节：-50% ~ +50%
    volume: str = "+0%"     # 音量调节：-50% ~ +50%
    pitch: str = "+0Hz"     # 音调调节：-50Hz ~ +50Hz


@app.get("/health")
async def health():
    """健康检查"""
    return {
        "status": "ok",
        "voice": DEFAULT_VOICE,
        "voices": list(SUPPORTED_VOICES.keys()),
    }


@app.get("/voices")
async def list_voices():
    """获取可用音色列表"""
    return JSONResponse(content=SUPPORTED_VOICES)


@app.post("/tts")
async def text_to_speech(request: TTSRequest):
    """
    文字转语音

    请求体:
        text:   待合成的文本（必填，最长 2000 字符）
        voice:  音色（默认 zh-CN-YunxiNeural）
        rate:   语速（默认 +0%，范围 -50%~+50%）
        volume: 音量（默认 +0%，范围 -50%~+50%）
        pitch:  音调（默认 +0Hz，范围 -50Hz~+50Hz）

    返回:
        {
            "audio":   "Base64 编码的 MP3 音频",
            "format":  "mp3",
            "size":    音频字节数,
            "elapsed": 处理耗时（毫秒）
        }
    """
    start_time = time.time()
    text = request.text.strip()

    if not text:
        raise HTTPException(status_code=400, detail="文本不能为空")

    # edge-tts 有最大文本限制，超长时截断
    if len(text) > 2000:
        log.warning("文本过长 (%d 字符)，截取前 2000 字符", len(text))
        text = text[:2000]

    log.info("TTS 请求: text=\"%s...\" (%d字符), voice=%s, rate=%s, volume=%s, pitch=%s",
             text[:50], len(text), request.voice, request.rate, request.volume, request.pitch)

    try:
        # 调用 edge-tts 生成语音（异步流式收集）
        communicate = edge_tts.Communicate(
            text,
            voice=request.voice,
            rate=request.rate,
            volume=request.volume,
            pitch=request.pitch,
        )

        # 收集所有音频块
        audio_data = b""
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                audio_data += chunk["data"]

        if not audio_data:
            raise HTTPException(status_code=500, detail="TTS 生成音频为空")

        # 转为 Base64
        audio_base64 = base64.b64encode(audio_data).decode("utf-8")

        elapsed_ms = int((time.time() - start_time) * 1000)
        audio_size_kb = len(audio_data) / 1024

        log.info("TTS 完成: size=%.1fKB, elapsed=%dms, voice=%s",
                 audio_size_kb, elapsed_ms, request.voice)

        return {
            "audio": audio_base64,
            "format": "mp3",
            "size": len(audio_data),
            "elapsed": elapsed_ms,
        }

    except Exception as e:
        log.error("TTS 失败: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Edge-TTS HTTP API 服务")
    parser.add_argument("--port", type=int, default=9091, help="服务端口 (默认: 9091)")
    parser.add_argument("--host", default="0.0.0.0", help="监听地址 (默认: 0.0.0.0)")
    parser.add_argument("--voice", default=DEFAULT_VOICE, help=f"默认音色 (默认: {DEFAULT_VOICE})")
    args = parser.parse_args()

    # 更新默认音色
    DEFAULT_VOICE = args.voice

    log.info("=" * 50)
    log.info("Edge-TTS 服务启动")
    log.info("地址: http://%s:%d", args.host, args.port)
    log.info("默认音色: %s (%s)", args.voice, SUPPORTED_VOICES.get(args.voice, "自定义"))
    log.info("API 端点:")
    log.info("  POST /tts    - 文字转语音")
    log.info("  GET  /health - 健康检查")
    log.info("  GET  /voices - 音色列表")
    log.info("=" * 50)

    uvicorn.run(app, host=args.host, port=args.port, log_level="info")

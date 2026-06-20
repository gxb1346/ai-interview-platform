FROM python:3.11-slim

WORKDIR /app

# 设置 HuggingFace 镜像（国内网络优化）
ENV HF_ENDPOINT=https://hf-mirror.com
ENV HF_HUB_DISABLE_SSL_VERIFY=1

# 复制依赖文件并安装 Python 包（faster-whisper 的 Linux wheel 是预编译的）
COPY requirements.txt .
RUN pip install --no-cache-dir --default-timeout=120 -r requirements.txt

# 复制服务脚本
COPY whisper_server.py .

# API 端口
EXPOSE 9090

# 默认启动命令（CPU 模式）
# 如需 GPU 支持，改为：--device cuda
CMD ["python", "whisper_server.py", "--model", "base", "--device", "cpu", "--port", "9090"]

@echo off
set AI_API_KEY=sk-1d953214ec864af6aec34b0e43fc85e5
set AI_MODEL=qwen-turbo
set AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode
set POSTGRES_PASSWORD=password
set SPRING_PROFILES_ACTIVE=dev

cd /d D:\project1\ai-interview-platform
.\gradlew.bat bootRun > build\backend.log 2>&1

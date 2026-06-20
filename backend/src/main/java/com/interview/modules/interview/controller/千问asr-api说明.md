非实时语音合成通过HTTP API将文本转换为语音，适用于有声书制作、在线教育配音、内容制作等对延迟要求不高的场景，支持丰富音色、多语言、声音复刻与声音设计。

## **概述**

通过HTTP API将完整文本转换为语音文件，支持非流式和流式两种输出模式。

-   **非流式**返回音频文件 URL，有效期 24 小时；**流式**逐段返回 PCM 音频数据。

-   支持多种语言，含中文方言。

-   支持[声音复刻](https://help.aliyun.com/zh/model-studio/voice-cloning-user-guide)与[声音设计](https://help.aliyun.com/zh/model-studio/voice-design-user-guide)进行定制音色创建。

-   支持[指令控制](#nrt-instruct-h3)，通过自然语言指令控制语音表现力。


低延迟流式场景请参见[实时语音合成](https://help.aliyun.com/zh/model-studio/realtime-tts-user-guide)。各模型选型建议请参见[语音合成](https://help.aliyun.com/zh/model-studio/tts-model/)。

## **前提条件**

开始前，请确认已完成以下准备工作：

-   [配置API Key](https://help.aliyun.com/zh/model-studio/get-api-key)，并[设置到环境变量](https://help.aliyun.com/zh/model-studio/configure-api-key-through-environment-variables)

-   （可选）如果通过 DashScope SDK调用，[安装最新版SDK](https://help.aliyun.com/zh/model-studio/install-sdk)


## **快速开始**

以下各 Tab 分别演示不同模型系列的语音合成。更多语言示例和详细参数说明，请参见[API 参考](#bb4dbbdb74em4)。

## **CosyVoice**

以下示例演示如何使用 CosyVoice 模型合成语音。

**重要**

CosyVoice 非实时语音合成仅在北京地域可用。

## **非流式输出**

非流式模式下，响应中包含合成音频的 URL，有效期为 24 小时。

```
curl -X POST https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer \
-H "Authorization: Bearer $DASHSCOPE_API_KEY" \
-H "Content-Type: application/json" \
-d '{
    "model": "cosyvoice-v3-flash",
    "input": {
      "text": "我家的后面有一个很大的花园。",
      "voice": "longanyang",
      "format": "wav",
      "sample_rate": 24000
    }
}'
```

## **流式输出**

添加 `X-DashScope-SSE: enable` Header 开启流式输出，服务端以 Server-Sent Events（SSE）方式逐段返回音频数据。

```
curl -X POST https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer \
-H "Authorization: Bearer $DASHSCOPE_API_KEY" \
-H "Content-Type: application/json" \
-H "X-DashScope-SSE: enable" \
-d '{
    "model": "cosyvoice-v3-flash",
    "input": {
      "text": "我家的后面有一个很大的花园。",
      "voice": "longanyang",
      "format": "wav",
      "sample_rate": 24000
    }
}'
```

## **Qwen-TTS**

本节所有示例均使用[系统音色](#bac280ddf5a1u)。

## **非流式输出**

非流式模式下，响应中包含 `url` 字段，指向合成的音频文件。URL 有效期为 24 小时。

## **Python**

```
import os
import dashscope

# 以下为华北2（北京）地域的URL，各地域的URL不同。
dashscope.base_http_api_url = 'https://dashscope.aliyuncs.com/api/v1'

text = "那我来给大家推荐一款T恤，这款呢真的是超级好看，这个颜色呢很显气质，而且呢也是搭配的绝佳单品，大家可以闭眼入，真的是非常好看，对身材的包容性也很好，不管啥身材的宝宝呢，穿上去都是很好看的。推荐宝宝们下单哦。"
# SpeechSynthesizer接口使用方法：dashscope.audio.qwen_tts.SpeechSynthesizer.call(...)
response = dashscope.MultiModalConversation.call(
    # 如需使用指令控制功能，请将model替换为qwen3-tts-instruct-flash
    model="qwen3-tts-flash",
    # 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
    # 若没有配置环境变量，请用阿里云百炼API Key将下行替换为：api_key = "sk-xxx"
    api_key=os.getenv("DASHSCOPE_API_KEY"),
    text=text,
    voice="Cherry",
    language_type="Chinese", # 建议与文本语种一致，以获得正确的发音和自然的语调。
    # 如需使用指令控制功能，请取消下方注释，并将model替换为qwen3-tts-instruct-flash
    # instructions='语速较快，带有明显的上扬语调，适合介绍时尚产品。',
    # optimize_instructions=True,
    stream=False
)
print(response)
```

## **Java**

需要导入 Gson 依赖，Maven 或 Gradle 添加方式如下：

## **Maven**

在`pom.xml`中添加：

```
<!-- https://mvnrepository.com/artifact/com.google.code.gson/gson -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.13.1</version>
</dependency>
```

## **Gradle**

在`build.gradle`中添加：

```
// https://mvnrepository.com/artifact/com.google.code.gson/gson
implementation("com.google.code.gson:gson:2.13.1")
```

```
import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.Constants;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

public class Main {
    // 如需使用指令控制功能，请将MODEL替换为qwen3-tts-instruct-flash
    private static final String MODEL = "qwen3-tts-flash";
    public static void call() throws ApiException, NoApiKeyException, UploadFileException {
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                // 若没有配置环境变量，请用阿里云百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .model(MODEL)
                .text("Today is a wonderful day to build something people love!")
                .voice(AudioParameters.Voice.CHERRY)
                .languageType("English") // 建议与文本语种一致，以获得正确的发音和自然的语调。
                // 如需使用指令控制功能，请取消下方注释，并将model替换为qwen3-tts-instruct-flash
                // .parameter("instructions","语速较快，带有明显的上扬语调，适合介绍时尚产品。")
                // .parameter("optimize_instructions",true)
                .build();
        MultiModalConversationResult result = conv.call(param);
        String audioUrl = result.getOutput().getAudio().getUrl();
        System.out.print(audioUrl);

        // 下载音频文件到本地
        try (InputStream in = new URL(audioUrl).openStream();
             FileOutputStream out = new FileOutputStream("downloaded_audio.wav")) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            System.out.println("\n音频文件已下载到本地：downloaded_audio.wav");
        } catch (Exception e) {
            System.out.println("\n下载音频文件时出错：" + e.getMessage());
        }
    }
    public static void main(String[] args) {
        try {
            // 以下为华北2（北京）地域的URL，各地域的URL不同。
            Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
            call();
        } catch (ApiException | NoApiKeyException | UploadFileException e) {
            System.out.println(e.getMessage());
        }
        System.exit(0);
    }
}
```

## **cURL**

```
# ======= 重要提示 =======
# 以下为华北2（北京）地域的URL，各地域的URL不同。
# 新加坡地域和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
# === 执行时请删除该注释 ===

curl -X POST 'https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation' \
-H "Authorization: Bearer $DASHSCOPE_API_KEY" \
-H 'Content-Type: application/json' \
-d '{
    "model": "qwen3-tts-flash",
    "input": {
        "text": "那我来给大家推荐一款T恤，这款呢真的是超级好看，这个颜色呢很显气质，而且呢也是搭配的绝佳单品，大家可以闭眼入，真的是非常好看，对身材的包容性也很好，不管啥身材的宝宝呢，穿上去都是很好看的。推荐宝宝们下单哦。",
        "voice": "Cherry",
        "language_type": "Chinese"
    }
}'
```

## **流式输出**

流式模式下，音频数据以 Base64 编码的 PCM 格式逐段返回，最后一个数据包中包含完整音频的 URL。

## **Python**

```
# coding=utf-8
#
# Installation instructions for pyaudio:
# APPLE Mac OS X
#   brew install portaudio
#   pip install pyaudio
# Debian/Ubuntu
#   sudo apt-get install python-pyaudio python3-pyaudio
#   or
#   pip install pyaudio
# CentOS
#   sudo yum install -y portaudio portaudio-devel && pip install pyaudio
# Microsoft Windows
#   python -m pip install pyaudio

import os
import dashscope
import pyaudio
import time
import base64
import numpy as np

# 以下为华北2（北京）地域的URL，各地域的URL不同。
dashscope.base_http_api_url = 'https://dashscope.aliyuncs.com/api/v1'

p = pyaudio.PyAudio()
# 创建音频流
stream = p.open(format=pyaudio.paInt16,
                channels=1,
                rate=24000,
                output=True)

text = "你好啊，我是千问"
response = dashscope.MultiModalConversation.call(
    # 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
    # 若没有配置环境变量，请用阿里云百炼API Key将下行替换为：api_key = "sk-xxx"
    api_key=os.getenv("DASHSCOPE_API_KEY"),
    # 如需使用指令控制功能，请将model替换为qwen3-tts-instruct-flash
    model="qwen3-tts-flash",
    text=text,
    voice="Cherry",
    language_type="Chinese",  # 建议与文本语种一致，以获得正确的发音和自然的语调。
    # 如需使用指令控制功能，请取消下方注释，并将model替换为qwen3-tts-instruct-flash
    # instructions='语速较快，带有明显的上扬语调，适合介绍时尚产品。',
    # optimize_instructions=True,
    stream=True
)

for chunk in response:
    if chunk.output is not None:
      audio = chunk.output.audio
      if audio.data is not None:
          wav_bytes = base64.b64decode(audio.data)
          audio_np = np.frombuffer(wav_bytes, dtype=np.int16)
          # 直接播放音频数据
          stream.write(audio_np.tobytes())
      if chunk.output.finish_reason == "stop":
          print(f"finish at: {chunk.output.audio.expires_at}")
time.sleep(0.8)
# 清理资源
stream.stop_stream()
stream.close()
p.terminate()
```

## **Java**

需要导入 Gson 依赖，Maven 或 Gradle 添加方式如下：

### **Maven**

在`pom.xml`中添加：

```
<!-- https://mvnrepository.com/artifact/com.google.code.gson/gson -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.13.1</version>
</dependency>
```

### **Gradle**

在`build.gradle`中添加：

```
// https://mvnrepository.com/artifact/com.google.code.gson/gson
implementation("com.google.code.gson:gson:2.13.1")
```

```
import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.Constants;
import io.reactivex.Flowable;
import javax.sound.sampled.*;
import java.util.Base64;

public class Main {
    // 如需使用指令控制功能，请将MODEL替换为qwen3-tts-instruct-flash
    private static final String MODEL = "qwen3-tts-flash";
    public static void streamCall() throws ApiException, NoApiKeyException, UploadFileException {
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                // 若没有配置环境变量，请用阿里云百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .model(MODEL)
                .text("Today is a wonderful day to build something people love!")
                .voice(AudioParameters.Voice.CHERRY)
                .languageType("English") // 建议与文本语种一致，以获得正确的发音和自然的语调。
                // 如需使用指令控制功能，请取消下方注释，并将model替换为qwen3-tts-instruct-flash
                // .parameter("instructions","语速较快，带有明显的上扬语调，适合介绍时尚产品。")
                // .parameter("optimize_instructions",true)
                .build();
        Flowable<MultiModalConversationResult> result = conv.streamCall(param);
        result.blockingForEach(r -> {
            try {
                // 1. 获取Base64编码的音频数据
                String base64Data = r.getOutput().getAudio().getData();
                byte[] audioBytes = Base64.getDecoder().decode(base64Data);

                // 2. 配置音频格式（根据API返回的音频格式调整）
                AudioFormat format = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        24000, // 采样率（需与API返回格式一致）
                        16,    // 采样位数
                        1,     // 声道数
                        2,     // 帧大小（字节数）
                        24000, // 帧率（需与采样率一致）
                        false  // 大端序
                );

                // 3. 实时播放音频数据
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                    if (line != null) {
                        line.open(format);
                        line.start();
                        line.write(audioBytes, 0, audioBytes.length);
                        line.drain();
                    }
                }
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        });
    }
    public static void main(String[] args) {
        // 以下为华北2（北京）地域的URL，各地域的URL不同。
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
        try {
            streamCall();
        } catch (ApiException | NoApiKeyException | UploadFileException e) {
            System.out.println(e.getMessage());
        }
        System.exit(0);
    }
}
```

## **cURL**

```
# ======= 重要提示 =======
# 以下为华北2（北京）地域的URL，各地域的URL不同。
# 新加坡地域和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
# === 执行时请删除该注释 ===

curl -X POST 'https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation' \
-H "Authorization: Bearer $DASHSCOPE_API_KEY" \
-H 'Content-Type: application/json' \
-H 'X-DashScope-SSE: enable' \
-d '{
    "model": "qwen3-tts-flash",
    "input": {
        "text": "那我来给大家推荐一款T恤，这款呢真的是超级好看，这个颜色呢很显气质，而且呢也是搭配的绝佳单品，大家可以闭眼入，真的是非常好看，对身材的包容性也很好，不管啥身材的宝宝呢，穿上去都是很好看的。推荐宝宝们下单哦。",
        "voice": "Cherry",
        "language_type": "Chinese"
    }
}'
```

## **MiniMax**

MiniMax 支持情感控制、语速调节和音调调整。

**重要**

MiniMax 非实时语音合成仅在北京地域可用。

## **非流式输出**

非流式模式下，响应中包含完整的合成音频。

```
curl -X POST "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation" \
-H "Authorization: Bearer $DASHSCOPE_API_KEY" \
-H "Content-Type: application/json" \
-d '{
  "model": "MiniMax/speech-2.8-hd",
  "input": {
    "text": "今天天气真不错，适合出去走走。",
    "voice_setting": {
      "voice_id": "male-qn-qingse",
      "speed": 1,
      "vol": 1,
      "pitch": 0,
      "emotion": "happy"
    },
    "audio_setting": {
      "sample_rate": 32000,
      "bitrate": 128000,
      "format": "mp3",
      "channel": 1
    }
  }
}'
```

## **流式输出**

添加 `X-DashScope-SSE: enable` Header 开启流式输出。

```
# 获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key

curl -X POST "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation" \
-H "Authorization: Bearer $DASHSCOPE_API_KEY" \
-H "Content-Type: application/json" \
-H "X-DashScope-SSE: enable" \
-d '{
  "model": "MiniMax/speech-2.8-hd",
  "input": {
    "text": "今天天气真不错，适合出去走走。",
    "voice_setting": {
      "voice_id": "male-qn-qingse",
      "speed": 1,
      "vol": 1,
      "pitch": 0,
      "emotion": "happy"
    },
    "audio_setting": {
      "sample_rate": 32000,
      "bitrate": 128000,
      "format": "mp3",
      "channel": 1
    }
  }
}'
```

## **进阶功能**

### **指令控制**

指令控制通过自然语言描述控制语音的音调、语速、情感和音色特点，无需调整复杂的音频参数。

**各模型指令规格**：

## **CosyVoice**

**支持的模型**：`cosyvoice-v3.5-plus`、`cosyvoice-v3.5-flash`、`cosyvoice-v3-plus`、`cosyvoice-v3-flash`

不同模型对指令的格式要求不同：

-   `cosyvoice-v3.5-plus`、`cosyvoice-v3.5-flash`：

    -   声音复刻/设计音色：可输入任意指令。

    -   系统音色：v3.5不支持系统音色。

-   `cosyvoice-v3-plus`：

    -   声音复刻/设计音色：不支持指令控制。

    -   系统音色：指令必须使用固定格式和内容，参见[CosyVoice音色列表](https://help.aliyun.com/zh/model-studio/cosyvoice-voice-list)。

-   `cosyvoice-v3-flash`：

    -   声音复刻/设计音色：可输入任意指令。

    -   系统音色：指令必须使用固定格式和内容，参见[CosyVoice音色列表](https://help.aliyun.com/zh/model-studio/cosyvoice-voice-list)。


**使用方式**：通过 `instruction` 参数指定指令内容。

**指令文本支持的语言**：

-   `cosyvoice-v3.5-plus`、`cosyvoice-v3.5-flash`：

    -   声音复刻/设计音色：中文、英文、法语、德语、日语、韩语、俄语、葡萄牙语、泰语、印尼语、越南语。

    -   系统音色：v3.5不支持系统音色。

-   `cosyvoice-v3-plus`：

    -   声音复刻/设计音色：中文、英文、法语、德语、日语、韩语、俄语。

    -   系统音色：指令必须使用固定格式和内容，参见[CosyVoice音色列表](https://help.aliyun.com/zh/model-studio/cosyvoice-voice-list)。

-   `cosyvoice-v3-flash`：

    -   声音复刻/设计音色：中文、英文、法语、德语、日语、韩语、俄语。

    -   系统音色：中文。


**指令文本长度限制**：不超过 100 字符。汉字（包括简体/繁体汉字、日文汉字和韩文汉字）按 2 个字符计算，其他字符（如标点符号、字母、数字、日韩文假名/谚文等）按 1 个字符计算。

## **Qwen-TTS**

**支持的模型**：仅支持千问3-TTS-Instruct-Flash 系列模型。

**使用方式**：通过 `instructions` 参数传入指令内容。

**指令文本支持的语言**：仅支持中文和英文。

**指令文本长度限制**：不超过 1,600 Token。

**适用场景**：

-   有声书和广播剧配音

-   广告和宣传片配音

-   游戏角色和动画配音

-   情感化的智能语音助手

-   纪录片和新闻播报


**如何编写高质量的声音描述**：

-   **核心原则**：

    1.  **具体而非模糊**：使用描绘声音特质的词语，如“低沉”、“清脆”、“语速偏快”，避免“好听”、“普通”等主观或模糊的表述。

    2.  **多维而非单一**：好的描述通常涵盖多个维度（如性别、年龄、情感等）。仅写“女声”过于宽泛，难以生成有特色的音色。

    3.  **客观而非主观**：聚焦声音的物理和感知特征。例如，用”音调偏高，带有活力“代替”我最喜欢的声音”。

    4.  **原创而非模仿**：描述声音的特质，而非要求模仿特定人物（如名人、演员）。模型不支持模仿，且可能涉及版权风险。

    5.  **简洁而非冗余**：确保每个词都有明确作用，避免重复的同义词或无意义的修饰。

-   **描述维度参考**：

    建议组合以下维度描述声音，维度越丰富，生成效果越精准。

    | **维度** | **描述示例** |
        | --- | --- |
    | 性别  | 男性、女性、中性 |
    | 年龄  | 儿童（5-12 岁）、青少年（13-18 岁）、青年（19-35 岁）、中年（36-55 岁）、老年（55 岁以上） |
    | 音调  | 高音、中音、低音、偏高、偏低 |
    | 语速  | 快速、中速、缓慢、偏快、偏慢 |
    | 情感  | 开朗、沉稳、温柔、严肃、活泼、冷静、治愈 |
    | 特点  | 有磁性、清脆、沙哑、圆润、甜美、浑厚、有力 |
    | 用途  | 新闻播报、广告配音、有声书、动画角色、语音助手、纪录片解说 |

-   **示例**：

    -   标准播音风格：吐字清晰精准，字正腔圆

    -   年轻活泼的女性声音，语速较快，带有明显的上扬语调，适合介绍时尚产品

    -   沉稳的中年男性，语速缓慢，音色低沉有磁性，适合朗读新闻或纪录片解说

    -   温柔知性的女性，30 岁左右，语调平和，适合有声书朗读

    -   可爱的儿童声音，大约 8 岁女孩，说话略带稚气，适合动画角色配音


### **方言**

本节介绍如何让模型用**中文方言**（如河南话、四川话等）输出语音。不同模型和音色类型的设置方式不同。

## **CosyVoice**

-   **系统音色**：在[CosyVoice音色列表](https://help.aliyun.com/zh/model-studio/cosyvoice-voice-list)中选择以下任一种音色：

    -   支持方言的系统音色（例如 `longshange_v3`），无需额外设置即可输出对应方言。

    -   支持[指令控制](#nrt-instruct-h3)且可指定方言的音色（例如 `longanhuan_v3`），通过指令文本指定方言。

-   **声音复刻音色**：通过[指令控制](#nrt-instruct-h3)功能设置，例如指令文本写 `请用河南话表达`。

-   **声音设计音色**：暂不支持方言。


**具体支持哪些方言**：参见[CosyVoice](https://help.aliyun.com/zh/model-studio/tts-model/#tts08-cosy02)中各模型“支持的语言”。

**示例**：以 `cosyvoice-v3-flash` + `longanhuan_v3` 音色，通过指令文本 `"请用河南话表达。"` 输出河南话语音。

```
curl -X POST https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer \
-H "Authorization: Bearer $DASHSCOPE_API_KEY" \
-H "Content-Type: application/json" \
-d '{
    "model": "cosyvoice-v3-flash",
    "input": {
      "text": "叫你去买盐，你买回来一袋面，这不是弄啥嘞吗！",
      "voice": "longanhuan_v3",
      "format": "wav",
      "sample_rate": 24000,
      "instruction": "请用河南话表达。"
    }
}'
```

## **Qwen-TTS**

-   **系统音色**：使用支持方言的系统音色，参见[Qwen-TTS音色列表](https://help.aliyun.com/zh/model-studio/qwen-tts-voice-list)。

-   **声音复刻音色**：不支持方言。

-   **声音设计音色**：不支持方言。


**具体支持哪些方言**：参见[Qwen3-TTS](https://help.aliyun.com/zh/model-studio/tts-model/#tts08-q3tts02)中各模型“支持的语言”。

## **适用范围**

**不同**[**地域**](https://help.aliyun.com/zh/model-studio/regions/)**支持的模型不同**：

## **华北2（北京）**

调用以下模型时，请选择北京地域的[API Key](https://bailian.console.aliyun.com/?tab=model#/api-key)：

-   **CosyVoice**：cosyvoice-v3.5-plus、cosyvoice-v3.5-flash、cosyvoice-v3-plus、cosyvoice-v3-flash、cosyvoice-v2

-   **Qwen-TTS**：

    -   **Qwen3-TTS-Instruct-Flash**：qwen3-tts-instruct-flash（稳定版，当前等同 qwen3-tts-instruct-flash-2026-01-26）、qwen3-tts-instruct-flash-2026-01-26（最新快照版）

    -   **Qwen3-TTS-VD****：**qwen3-tts-vd-2026-01-26（最新快照版）

    -   **Qwen3-TTS-VC****：**qwen3-tts-vc-2026-01-22（最新快照版）

    -   **Qwen3-TTS-Flash**：qwen3-tts-flash（稳定版，当前等同 qwen3-tts-flash-2025-11-27）、qwen3-tts-flash-2025-11-27、qwen3-tts-flash-2025-09-18

    -   **Qwen-TTS**：qwen-tts（稳定版，当前等同 qwen-tts-2025-04-10）、qwen-tts-latest（最新版，当前等同 qwen-tts-2025-05-22）、qwen-tts-2025-05-22（快照版）、qwen-tts-2025-04-10（快照版）

-   **MiniMax**：MiniMax/speech-2.8-hd、MiniMax/speech-02-hd、MiniMax/speech-2.8-turbo、MiniMax/speech-02-turbo


## **新加坡**

调用以下模型时，请选择新加坡地域的[API Key](https://modelstudio.console.aliyun.com/?tab=dashboard#/api-key)：

-   **Qwen-TTS**：

    -   **Qwen3-TTS-Instruct-Flash**：qwen3-tts-instruct-flash（稳定版，当前等同 qwen3-tts-instruct-flash-2026-01-26）、qwen3-tts-instruct-flash-2026-01-26（最新快照版）

    -   **Qwen3-TTS-VD****：**qwen3-tts-vd-2026-01-26（最新快照版）

    -   **Qwen3-TTS-VC****：**qwen3-tts-vc-2026-01-22（最新快照版）

    -   **Qwen3-TTS-Flash**：qwen3-tts-flash（稳定版，当前等同 qwen3-tts-flash-2025-11-27）、qwen3-tts-flash-2025-11-27、qwen3-tts-flash-2025-09-18


## **支持的系统音色**

不同模型支持的音色不同。将请求参数 `voice` 设为下表中 **voice 参数**列的值即可。

-   [CosyVoice音色列表](https://help.aliyun.com/zh/model-studio/cosyvoice-voice-list)

-   [Qwen-TTS音色列表](https://help.aliyun.com/zh/model-studio/qwen-tts-voice-list#261bc7f209ri2)


## **API 参考**

-   [非实时语音合成-CosyVoice API参考](https://help.aliyun.com/zh/model-studio/non-realtime-cosyvoice-api/)

-   [非实时语音合成-千问API参考](https://help.aliyun.com/zh/model-studio/qwen-tts-api)

-   [非实时语音合成-MiniMax API参考](https://help.aliyun.com/zh/model-studio/minimax-speech-synthesis/)


## **常见问题**

### **Q：音频文件链接的有效期是多久？**

A：音频文件链接在生成后 24 小时内有效。链接过期后，重新调用接口即可获取新链接。

span.aliyun-docs-icon { color: transparent !important; font-size: 0 !important; } span.aliyun-docs-icon:before { color: black; font-size: 16px; } span.aliyun-docs-icon.icon-size-20:before { font-size: 20px; } span.aliyun-docs-icon.icon-size-22:before { font-size: 22px; } span.aliyun-docs-icon.icon-size-24:before { font-size: 24px; } span.aliyun-docs-icon.icon-size-26:before { font-size: 26px; } span.aliyun-docs-icon.icon-size-28:before { font-size: 28px; }
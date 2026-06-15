package com.interview.modules.interview.controller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ASR 热词配置
 * 用于提升技术术语在语音识别中的准确率
 * DashScope paraformer-realtime-v2 的 hotwords 参数：
 *   - key: 热词文本（不区分大小写）
 *   - value: 提升权重（1-5），越大越倾向于识别为该词
 */
public class TechHotwords {

    /** 常用技术热词及其权重 */
    public static final Map<String, Integer> HOTWORDS = createHotwords();

    private static Map<String, Integer> createHotwords() {
        Map<String, Integer> map = new LinkedHashMap<>();

        // ===== 编程语言 =====
        map.put("Java", 5);
        map.put("java", 5);
        map.put("Python", 5);
        map.put("python", 5);
        map.put("JavaScript", 5);
        map.put("javascript", 4);
        map.put("TypeScript", 5);
        map.put("typescript", 4);
        map.put("Go", 5);
        map.put("Golang", 5);
        map.put("golang", 4);
        map.put("Rust", 5);
        map.put("rust", 4);
        map.put("C++", 4);
        map.put("C#", 4);
        map.put("Kotlin", 4);
        map.put("kotlin", 4);
        map.put("Scala", 4);
        map.put("scala", 3);
        map.put("SQL", 5);
        map.put("sql", 5);
        map.put("NoSQL", 4);
        map.put("nosql", 4);

        // ===== 框架与中间件 =====
        map.put("Spring Boot", 5);
        map.put("springboot", 5);
        map.put("spring boot", 5);
        map.put("Springboot", 5);
        map.put("Spring Cloud", 5);
        map.put("springcloud", 5);
        map.put("spring cloud", 5);
        map.put("MyBatis", 5);
        map.put("mybatis", 5);
        map.put("MyBatis Plus", 5);
        map.put("mybatis plus", 5);
        map.put("Redis", 5);
        map.put("redis", 5);
        map.put("RabbitMQ", 5);
        map.put("rabbitmq", 4);
        map.put("Kafka", 5);
        map.put("kafka", 4);
        map.put("RocketMQ", 5);
        map.put("rocketmq", 4);
        map.put("Elasticsearch", 5);
        map.put("elasticsearch", 4);
        map.put("Nginx", 4);
        map.put("nginx", 4);
        map.put("Docker", 5);
        map.put("docker", 5);
        map.put("Kubernetes", 5);
        map.put("kubernetes", 4);
        map.put("K8s", 5);
        map.put("k8s", 5);

        // ===== 数据库 =====
        map.put("MySQL", 5);
        map.put("mysql", 5);
        map.put("PostgreSQL", 5);
        map.put("postgresql", 4);
        map.put("postgres", 4);
        map.put("MongoDB", 5);
        map.put("mongodb", 4);
        map.put("Oracle", 4);
        map.put("oracle", 4);

        // ===== 云服务与架构 =====
        map.put("AWS", 4);
        map.put("aws", 4);
        map.put("Docker Compose", 4);
        map.put("docker compose", 4);
        map.put("Microservice", 4);
        map.put("microservice", 4);
        map.put("Microservices", 4);
        map.put("microservices", 4);
        map.put("微服务", 5);

        // ===== 协议与标准 =====
        map.put("RESTful", 4);
        map.put("restful", 4);
        map.put("REST API", 4);
        map.put("rest api", 4);
        map.put("gRPC", 4);
        map.put("grpc", 4);
        map.put("HTTP", 4);
        map.put("http", 4);
        map.put("HTTPS", 4);
        map.put("https", 4);
        map.put("WebSocket", 4);
        map.put("websocket", 4);
        map.put("OAuth", 4);
        map.put("oauth", 4);
        map.put("JWT", 4);
        map.put("jwt", 4);

        // ===== 工具与平台 =====
        map.put("Git", 4);
        map.put("git", 4);
        map.put("Maven", 4);
        map.put("maven", 4);
        map.put("Gradle", 4);
        map.put("gradle", 4);
        map.put("Jenkins", 4);
        map.put("jenkins", 4);
        map.put("GitHub", 4);
        map.put("github", 4);
        map.put("GitLab", 4);
        map.put("gitlab", 4);
        map.put("Linux", 4);
        map.put("linux", 4);
        map.put("Mac", 3);
        map.put("Windows", 3);
        map.put("windows", 3);

        // ===== 大数据与 AI =====
        map.put("Spark", 4);
        map.put("spark", 4);
        map.put("Flink", 4);
        map.put("flink", 4);
        map.put("Hadoop", 4);
        map.put("hadoop", 4);
        map.put("TensorFlow", 4);
        map.put("tensorflow", 4);
        map.put("PyTorch", 4);
        map.put("pytorch", 4);
        map.put("Transformer", 4);
        map.put("transformer", 4);
        map.put("LLM", 5);
        map.put("llm", 5);
        map.put("API", 5);
        map.put("api", 5);
        map.put("SDK", 4);
        map.put("sdk", 4);

        // ===== 其他常见技术缩写 =====
        map.put("IoT", 4);
        map.put("iot", 4);
        map.put("DevOps", 4);
        map.put("devops", 4);
        map.put("CI/CD", 4);
        map.put("ci/cd", 4);
        map.put("JVM", 5);
        map.put("jvm", 5);
        map.put("ORM", 4);
        map.put("orm", 4);
        map.put("AOP", 4);
        map.put("aop", 4);
        map.put("IOC", 4);
        map.put("ioc", 4);
        map.put("DDD", 4);
        map.put("ddd", 4);
        map.put("RPC", 4);
        map.put("rpc", 4);
        map.put("CDN", 4);
        map.put("cdn", 4);
        map.put("CPU", 3);
        map.put("cpu", 3);
        map.put("GPU", 4);
        map.put("gpu", 4);
        map.put("RAM", 3);
        map.put("ram", 3);
        map.put("SSD", 3);
        map.put("ssd", 3);

        return Map.copyOf(map);
    }

    private TechHotwords() {}
}

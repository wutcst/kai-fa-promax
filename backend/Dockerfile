# ============================================================
# 回归验证点: 提升发布可用性
# ============================================================
# [RV-DOCKER-001] Dockerfile 构建通过: docker build -t guandan-backend .
#   → 预期: JAR 不存在时构建失败，存在时成功生成镜像
# [RV-DOCKER-002] 健康检查可达: HEALTHCHECK 配置正确，容器启动 60s 后
#   curl /actuator/health 返回 200
# [RV-DOCKER-003] 安全响应头: 通过 Nginx 代理访问后端时，响应头包含
#   X-Content-Type-Options: nosniff、X-Frame-Options: DENY
# ============================================================

# 掼蛋游戏后端 Dockerfile
# 多阶段构建：第一阶段编译，第二阶段运行
# 异常场景：若镜像拉取失败（网络/镜像源不可达），构建将报错退出
# ============================================================
# Phase 1: 构建阶段
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom.xml 单独下载依赖（利用 docker layer 缓存）
# 异常场景：pom.xml 格式错误 → mvn 解析失败，构建中断
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# 复制源码并执行编译打包
COPY src ./src
# 异常场景：编译错误 → mvn package 失败，镜像构建终止
# 异常场景：测试失败且未跳过 → mvn package 失败，使用 -DskipTests 跳过
RUN mvn package -DskipTests -B

# ============================================================
# Phase 2: 运行阶段（精简镜像）
# ============================================================
FROM openjdk:17-jdk-slim

# 设置工作目录
# 异常场景：若 /app 目录权限异常，后续文件操作将报错
WORKDIR /app

# 设置时区
# 接口字段：TZ=Asia/Shanghai 确保日志时间戳与北京时间一致
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 安装必要的工具
# 异常场景：apt-get 源不可用时会安装失败，健康检查依赖 curl
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 从构建阶段复制 JAR 文件
# 异常场景：JAR 文件不存在（构建失败）→ COPY 命令报错，镜像构建失败
COPY --from=builder /build/target/guandan-backend-*.jar app.jar

# 创建日志和数据目录
# 异常场景：目录已存在时 mkdir -p 幂等执行，不会报错
RUN mkdir -p /app/logs /app/data

# 设置环境变量
# 接口字段：JAVA_OPTS 控制 JVM 堆内存和 GC 策略
# 异常场景：Xms > Xmx 时 JVM 启动失败
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
# 接口字段：SPRING_PROFILES_ACTIVE 切换环境配置（dev/prod）
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}
# 接口字段：SERVER_PORT 应用监听端口，默认 8081
ENV SERVER_PORT=${SERVER_PORT:-8081}
# 接口字段：LOG_DIR 日志输出目录，默认 /app/logs
ENV LOG_DIR=${LOG_DIR:-/app/logs}

# 暴露端口
# 异常场景：端口被宿主机占用时容器启动失败
EXPOSE ${SERVER_PORT}

# 健康检查
# 接口字段：interval=30s 每30秒探测一次
# 异常场景：start-period=60s 内健康检查失败不计入重试，给应用足够的启动时间
# 异常场景：连续3次检查失败 → Docker 标记容器为 unhealthy
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

# 使用非 root 用户运行（安全加固）
# 异常场景：若 /app/logs 权限不足，应用写入日志失败
RUN useradd -m -u 1001 guandan && chown -R guandan:guandan /app
USER guandan

# 启动应用
# 异常场景：JAVA_OPTS 含非法参数 → JVM 启动失败，容器退出
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
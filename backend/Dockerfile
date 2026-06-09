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
# 使用OpenJDK 17作为基础镜像
# 异常场景：若镜像拉取失败（网络/镜像源不可达），构建将报错退出
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

# 复制Maven构建的JAR文件
# 异常场景：JAR 文件不存在（构建失败）→ COPY 命令报错，镜像构建失败
COPY target/guandan-backend-*.jar app.jar

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

# 暴露端口
# 异常场景：端口被宿主机占用时容器启动失败
EXPOSE ${SERVER_PORT}

# 健康检查
# 接口字段：interval=30s 每30秒探测一次
# 异常场景：start-period=60s 内健康检查失败不计入重试，给应用足够的启动时间
# 异常场景：连续3次检查失败 → Docker 标记容器为 unhealthy
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

# 启动应用
# 异常场景：JAVA_OPTS 含非法参数 → JVM 启动失败，容器退出
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

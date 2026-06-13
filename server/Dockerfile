# ============================================================
# Stage 1: Maven build
# ============================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 1a. 复制 Maven 配置（变更频率最低 → 缓存最久）
COPY pom.xml .
COPY settings.xml /root/.m2/settings.xml

# 1b. 预下载依赖（pom.xml 不变时此层命中缓存）
RUN mvn dependency:go-offline -B -s /root/.m2/settings.xml

# 1c. 复制源码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B -s /root/.m2/settings.xml

# ============================================================
# Stage 2: JRE runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="stardew-planner"
LABEL description="Stardew Valley Planting Planner - Backend API"

# 时区 + curl（用于健康检查）
RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# 非 root 用户运行
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 复制构建产物（精确文件名，避免通配符匹配到 sources jar）
COPY --from=build --chown=spring:spring /app/target/planner-1.0.0.jar app.jar

EXPOSE 8080

# 健康检查：Spring Boot Actuator /actuator/health
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

# JVM 容器优化参数
ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# 优雅停机（docker stop 发 SIGTERM → Spring 完成请求后再退出）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

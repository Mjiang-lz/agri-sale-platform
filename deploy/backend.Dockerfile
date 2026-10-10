# syntax=docker/dockerfile:1
#
# 后端服务镜像（Spring Boot + MyBatis-Plus）。
#
# 构建上下文是**仓库根目录**（见 docker-compose.yml），
# 所以下面的 COPY 路径都带 backend/ 前缀。

# ---------------------------------------------------------------------------
# 构建
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# 先只拷 pom 把依赖拉齐并单独成层：以后改业务代码时这一层直接命中缓存，不必重下依赖。
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
# 跳过测试：单元测试的职责在 CI / 本地 mvn test，不该拖慢镜像构建，
# 也不该因为测试挂了就产出不了一个可部署的镜像（两件事的失败含义不同）。
RUN mvn -B -DskipTests package

# ---------------------------------------------------------------------------
# 运行
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 以非 root 运行。先把 /app 的属主交给它，上传目录才能写入 ——
# 命名卷首次挂载时会继承镜像里这个目录的属主，所以这一步必须在挂载之前做。
RUN useradd --system --uid 10001 appuser \
 && mkdir -p /app/uploads \
 && chown -R appuser:appuser /app

COPY --from=build --chown=appuser:appuser /src/target/*.jar /app/app.jar

USER appuser

# 数据源、JWT 密钥、接口文档开关都走 application-docker.yml 里定义的环境变量，
# 见 docker-compose.yml。OBS 未启用时上传产物落在 /app/uploads（对应卷）。
ENV SPRING_PROFILES_ACTIVE=docker \
    TZ=Asia/Shanghai

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

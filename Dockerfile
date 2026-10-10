FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ARG MODULE
COPY ${MODULE}/target/${MODULE}-1.0.0.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -Duser.timezone=Asia/Shanghai"
ENTRYPOINT ["java","-jar","/app/app.jar"]

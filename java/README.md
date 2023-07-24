# otel java extension
可以自定义 provider 包括： 自定义trace-id，Sampler，Exporter，propagator

使用 extension：
```shell
java -javaagent:path/to/opentelemetry-javaagent.jar \
     -Dotel.javaagent.extensions=build/libs/opentelemetry-java-instrumentation-extension-demo-1.0-all.jar \
     # ... env
     -jar myapp.jar
```

目前支持将指标和链路发送到观测云。

## 自定义 exporter
guance-exporter 将数据转成行协议格式发送到 Dataway。

```shell
# 不建议使用命令行形式，而是使用环境变量形式，这样 token 不会出现在链路数据中。
export OTEL_EXPORTER_GUANCE_ENDPOINT="https://openway.guance.com"
export OTEL_EXPORTER_GUANCE_TOKEN=tkn_f1650xxxxxxxxxxxxxxxxxx

java -javaagent:opentelemetry-javaagent.jar -Dotel.javaagent.extensions=guance-java-extension.jar -jar springboot-server.jar
```
# Golang guance-exporter

如何使用：

```go
// If the OpenTelemetry Collector is running on a local cluster (minikube or
    // microk8s), it should be accessible through the NodePort service at the
    // `localhost:30080` endpoint. Otherwise, replace `localhost` with the
    // endpoint of your cluster. If you run the app inside k8s, then you can
    // probably connect directly to the service through dns
    conn, err := grpc.DialContext(ctx, "10.200.14.226:4317", grpc.WithTransportCredentials(insecure.NewCredentials()), grpc.WithBlock())
    handleErr(err, "failed to create gRPC connection to collector")
    // Set up a trace exporter
    traceExporter, err := otlptracegrpc.New(ctx, otlptracegrpc.WithGRPCConn(conn))
    handleErr(err, "failed to create trace exporter")

    bsp = sdktrace.NewBatchSpanProcessor(traceExporter)
```

todo: 添加endpoint和token
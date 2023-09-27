package com.guance.javaagent;

import com.guance.javaagent.http.OKHTTPClient;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.internal.ThrottlingLogger;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.*;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import org.influxdb.dto.Point;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.guance.javaagent.http.OKHTTPClient.METRIC_CATEGORY;

public class DemoMetricExporter implements MetricExporter {
    private static final Logger internalLogger =
            Logger.getLogger(DemoMetricExporter.class.getName());
    private final ThrottlingLogger logger = new ThrottlingLogger(internalLogger);
    public static final String METRIC_NAME = "otel-service";
    private final OKHTTPClient delegate;

    public DemoMetricExporter() {
        logger.log(Level.INFO, "init GuanceMetricExporter");
        delegate = new OKHTTPClient();
    }

    public DemoMetricExporter setEndpoint( String endpoint) {
        delegate.setEndpoint(endpoint);
        return this;
    }

    public DemoMetricExporter setToken( String token) {
        if (token.equals("")) {
            logger.log(
                    Level.WARNING, "openway token is null !!! please set otel.exporter.guance.token=xxx");
        }
        delegate.setToken(token);
        return this;
    }

    @Override
    public CompletableResultCode export(Collection<MetricData> collection) {
        //System.out.println("span exporter");
        StringBuilder sb = new StringBuilder();
        for (MetricData metricData : collection) {
            sb.append(convertToLineProtocol(metricData));
        }

        delegate.write(sb.toString(), collection.size(), METRIC_CATEGORY);
        return CompletableResultCode.ofSuccess();
    }

    public String convertToLineProtocol(MetricData metricData) {
        StringBuilder sb = new StringBuilder();

        String name = metricData.getName();
        MetricDataType type = metricData.getType();
        Data<?> datas = metricData.getData();
        for (PointData point : datas.getPoints()) {
            switch (type) {
                case DOUBLE_GAUGE:
                case DOUBLE_SUM:
                {
                    DoublePointData dp = (DoublePointData) point;
                    sb.append(
                                    toPointDB(
                                            name,
                                            dp.getValue(),
                                            dp.getAttributes(),
                                            metricData.getResource().getAttributes(),
                                            dp.getEpochNanos()))
                            .append("\n");
                    break;
                }
                case LONG_GAUGE:
                case LONG_SUM:
                {
                    LongPointData lp = (LongPointData) point;
                    sb.append(
                                    toPointDB(
                                            name,
                                            lp.getValue(),
                                            lp.getAttributes(),
                                            metricData.getResource().getAttributes(),
                                            lp.getEpochNanos()))
                            .append("\n");
                    break;
                }
                case HISTOGRAM:
                case EXPONENTIAL_HISTOGRAM:
                case SUMMARY:
                { // todo 观测云页面没有该类型的数据，可以暂时不接入
                    break;
                }
            }
        }

        return sb.toString();
    }

    public String toPointDB(
            String name, double value, Attributes attributes, Attributes attributes1, Long nanos) {
        Point.Builder pointBuilder =
                Point.measurement(METRIC_NAME).addField(name, value).tag("host","hostname");

        attributes.forEach((key, v) -> pointBuilder.tag(key.getKey(), v.toString()));
        attributes1.forEach((key, v) -> pointBuilder.tag(key.getKey(), v.toString()));

        pointBuilder.time(nanos, TimeUnit.NANOSECONDS);

        return pointBuilder.build().lineProtocol();
    }

    public String toPointDB(
            String name, long value, Attributes attributes, Attributes attributes1, Long nanos) {

        Point.Builder pointBuilder =
                Point.measurement(METRIC_NAME).addField(name, value).tag("host","hostname");

        attributes.forEach((key, v) -> pointBuilder.tag(key.getKey(), v.toString()));
        attributes1.forEach((key, v) -> pointBuilder.tag(key.getKey(), v.toString()));

        pointBuilder.time(nanos, TimeUnit.NANOSECONDS);

        return pointBuilder.build().lineProtocol();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
        return AggregationTemporalitySelector.deltaPreferred()
                .getAggregationTemporality(instrumentType);
    }
}

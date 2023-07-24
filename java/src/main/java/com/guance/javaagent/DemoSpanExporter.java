/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.guance.javaagent;

import com.guance.javaagent.http.OKHTTPClient;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.internal.ThrottlingLogger;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.influxdb.dto.Point;

import java.sql.SQLOutput;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.guance.javaagent.http.OKHTTPClient.TRACE_CATEGORY;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

/**
 * See <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/sdk.md#span-exporter">
 * OpenTelemetry Specification</a> for more information about {@link SpanExporter}.
 *
 * @see DemoAutoConfigurationCustomizerProvider
 */
public class DemoSpanExporter implements SpanExporter {
  private static final Logger internalLogger = Logger.getLogger(DemoSpanExporter.class.getName());

  private final ThrottlingLogger logger = new ThrottlingLogger(internalLogger);
  private static final String SERVICE_NAME = "service.name";
  private static final String CONTAINER_NAME = "container.name";
  private static final String NAME = "opentelemetry";
  private static final AttributeKey<String> SERVICE_NAME_KEY = AttributeKey.stringKey(SERVICE_NAME);
  private static final AttributeKey<String> CONTAINER_NAME_KEY = AttributeKey.stringKey(CONTAINER_NAME);
  private final OKHTTPClient delegate;

  public DemoSpanExporter() {
    delegate = new OKHTTPClient();
  }

  public void setEndpoint(String endpoint) {
    requireNonNull(endpoint, "endpoint");
    if (endpoint.equals("")) {
      logger.log(
              WARNING,
              "guance endpoing is not set. please set otel.exporter.guance.endpoint=<openway>");
    } else {
      delegate.setEndpoint(endpoint);
    }
  }

  public void setToken(String token) {
    requireNonNull(token, "token");
    if (token.equals("")) {
      logger.log(WARNING, "openway token is null !!! please set otel.exporter.guance.token=xxx");
    } else {
      delegate.setToken(token);
    }
  }
  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    System.out.println("span exporter");
    StringBuilder sb = new StringBuilder();

    for (SpanData span : spans) {
      TraceFlags flags = span.getSpanContext().getTraceFlags();
      if (flags != null) {
        if (!flags.isSampled()) {
          logger.log(FINE,"sampled .....");
          continue;
        }
      }
      sb.append(convertSpanToInfluxDBPoint(span).lineProtocol()).append('\n');
    }
    sb.deleteCharAt(sb.length() - 1); // delete last \n

    delegate.write(sb.toString(), spans.size(), TRACE_CATEGORY);

    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }
  public Point convertSpanToInfluxDBPoint(SpanData span) {
    String serviceName = span.getResource().getAttributes().get(SERVICE_NAME_KEY);
    if (serviceName == null) {
      serviceName = "UNKNOWN";
    }
    String container = span.getResource().getAttributes().get(CONTAINER_NAME_KEY);

    String name = span.getName();
    long startTime = TimeUnit.NANOSECONDS.toMicros(span.getStartEpochNanos());
    long endTime = TimeUnit.NANOSECONDS.toMicros(span.getEndEpochNanos());
    long duration = endTime - startTime;
    String sourceType = getSourceType(span.getAttributes());
    String spanType = getSpanType(span.getParentSpanId());

    Point.Builder pointBuilder =
            Point.measurement(NAME)
                    .tag("service", serviceName)
                    .tag("service_name", serviceName)
                    .tag("operation", name)
                    .tag("source_type", sourceType)
                    .tag("span_type", spanType)
                    .tag("status",getStatusCode(span.getStatus().getStatusCode()))
                    //.tag("host", GuanceUtils.getHostName())
                    .addField("trace_id", span.getSpanContext().getTraceId())
                    .addField("span_id", span.getSpanContext().getSpanId())
                    .addField("parent_id", span.getParentSpanId())
                    .addField("start", startTime)
                    .addField("resource", name)
                    // .addField("message", span.toString())
                    .addField("duration", duration);
    if (container != null){
      pointBuilder.tag(CONTAINER_NAME,container);
    }

    span.getAttributes()
            .forEach(
                    (key, value) ->
                            pointBuilder.tag(key.getKey(), value.toString()));

    for (EventData event : span.getEvents()) {
      if (event.getName().equals("exception") ){
        event.getAttributes().forEach((key,val)-> pointBuilder.addField(key.getKey(),val.toString()));
      }
    }

    pointBuilder.time(span.getStartEpochNanos(), TimeUnit.NANOSECONDS);

    return pointBuilder.build();
  }

  public static String getSourceType(Attributes attributes) {
    AttributeKey<String> httpMethodKey = AttributeKey.stringKey("http.method");
    AttributeKey<String> rpcKey = AttributeKey.stringKey("rpc.system");
    AttributeKey<String> dbSystemKey = AttributeKey.stringKey("db.system");
    AttributeKey<String> messagingSystemKey = AttributeKey.stringKey("messaging.system");

    if (attributes.get(httpMethodKey) != null||attributes.get(rpcKey)!=null) {
      return "web";
    } else if (attributes.get(dbSystemKey) != null) {
      return "db";
    } else if (attributes.get(messagingSystemKey) != null) {
      return "message";
    } else {
      return "custom";
    }
  }

  public String getStatusCode(StatusCode code){
    switch (code){
      case ERROR:
        return "error";
      case UNSET:
      case OK:
      default:
        return "ok";
    }
  }

  public String getSpanType(String parentId){
    if(parentId==null){
      return "entry";
    }
    return  ( parentId.equals("")||parentId.equals("0000000000000000"))?"entry":"local";
  }
}

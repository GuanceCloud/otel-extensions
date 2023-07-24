/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.guance.javaagent;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.util.HashMap;
import java.util.Map;

/**
 * This is one of the main entry points for Instrumentation Agent's customizations. It allows
 * configuring the {@link AutoConfigurationCustomizer}. See the {@link
 * #customize(AutoConfigurationCustomizer)} method below.
 *
 * <p>Also see https://github.com/open-telemetry/opentelemetry-java/issues/2022
 *
 * @see AutoConfigurationCustomizerProvider
 * @see DemoPropagatorProvider
 */
@AutoService(AutoConfigurationCustomizerProvider.class)
public class DemoAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration
        .addTracerProviderCustomizer(this::configureSdkTracerProvider)
        .addMetricExporterCustomizer(this::configureSdkMetricProvider);
     //   .addPropertiesSupplier(this::getDefaultProperties);
  }

  private MetricExporter configureSdkMetricProvider(MetricExporter metricExporter, ConfigProperties config) {
    String endpoint = config.getString("otel.exporter.guance.endpoint");
    if (endpoint == null) {
      endpoint = "";
    }

    String token = config.getString("otel.exporter.guance.token");
    if (token == null) {
      token = "";
    }

    DemoMetricExporter exporter = new DemoMetricExporter();
     return exporter.setToken(token).setEndpoint(endpoint);
  }

  private SdkTracerProviderBuilder configureSdkTracerProvider(
      SdkTracerProviderBuilder tracerProvider, ConfigProperties config) {
    DemoSpanExporter spanProcessor = new DemoSpanExporter();
    String endpoint = config.getString("otel.exporter.guance.endpoint");
    if (endpoint != null) {
      spanProcessor.setEndpoint(endpoint);
    }

    String token = config.getString("otel.exporter.guance.token");
    if (token != null) {
      spanProcessor.setToken(token);
    }

    return tracerProvider
       // .setIdGenerator(new DemoIdGenerator()) 不设置，使用默认随机ID
        .setSpanLimits(SpanLimits.builder().setMaxNumberOfAttributes(1024).build())
        .addSpanProcessor(new DemoSpanProcessor())
        .addSpanProcessor(SimpleSpanProcessor.create(spanProcessor));
  }

  private Map<String, String> getDefaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.exporter.otlp.endpoint", "http://backend:8080");
    properties.put("otel.exporter.otlp.insecure", "true");
    properties.put("otel.config.max.attrs", "16");
    properties.put("otel.traces.sampler", "demo");
    return properties;
  }
}

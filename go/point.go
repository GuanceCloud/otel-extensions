package guance

import (
	"bytes"
	"encoding/hex"
	"errors"
	"fmt"
	influxdb "github.com/influxdata/influxdb1-client/v2"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	tracesdk "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
	"log"
	"time"
)

const (
	httpKey    = attribute.Key("http.method")
	rpcKey     = attribute.Key("rpc.system")
	dbKey      = attribute.Key("db.system")
	messageKey = attribute.Key("messaging.system")
)

var (
	name = "opentelemetry"
)

func ToPoint(ss []tracesdk.ReadOnlySpan) []byte {
	lines := make([][]byte, 0)

	for _, span := range ss {
		line, err := enqueueSpan(span)
		if err == nil {
			lines = append(lines, []byte(line))
		}
	}

	buf := bytes.Join(lines, []byte{'\n'})
	log.Println(len(buf)) // 请求的 body
	return buf
}

func enqueueSpan(span tracesdk.ReadOnlySpan) (ptS string, err error) {
	defer func() {
		if r := recover(); r != nil {
			var rerr error
			switch v := r.(type) {
			case error:
				rerr = v
			case string:
				rerr = errors.New(v)
			default:
				rerr = fmt.Errorf("%+v", r)
			}
			log.Println(rerr)
			//err = multierr.Combine(err, rerr)
		}
	}()

	traceID := span.SpanContext().TraceID()

	if traceID.IsValid() {
		err = errors.New("span has no trace ID")
		return
	}
	spanID := span.SpanContext().SpanID()
	if spanID.IsValid() {
		err = errors.New("span has no span ID")
		return
	}
	parentID := span.Parent().SpanID()
	if parentID.IsValid() {
		err = errors.New("span has no parentSpan ID")
		return
	}
	serviceName := ""
	for _, val := range span.Resource().Attributes() {
		if val.Key == "service.name" {
			serviceName = val.Value.AsString()
		}
	}
	if serviceName == "" {
		serviceName = "UNKNOWN"
	}
	sourceType := getSourceType(span.Attributes())
	spanType := getSpanType(span.Parent())

	tags := make(map[string]string, 0)
	fields := make(map[string]interface{}, 0)

	tags["service"] = serviceName
	tags["service_name"] = serviceName
	tags["operation"] = span.Name()
	tags["source_type"] = sourceType
	tags["span_type"] = spanType
	tags["status"] = getStatus(span.Status())

	fields["trace_id"] = hex.EncodeToString(traceID[:])
	fields["span_id"] = hex.EncodeToString(spanID[:])
	fields["parent_id"] = hex.EncodeToString(parentID[:])

	startTime := span.StartTime()
	if startTime.IsZero() {
		err = errors.New("span has no timestamp")
		return
	}

	endTime := span.EndTime()
	if startTime.IsZero() {
		err = errors.New("span has no end timestamp")
		return
	}
	duration := endTime.Sub(startTime).Nanoseconds()

	fields["duration"] = duration

	for _, val := range span.Attributes() {
		tags[string(val.Key)] = val.Value.AsString()
	}

	for _, event := range span.Events() {
		if event.Name == "exception" {
			for _, value := range event.Attributes {
				fields[string(value.Key)] = value.Value.AsString()
			}
		}
	}

	tags["trace_state"] = span.SpanContext().TraceState().String()

	pt, err := influxdb.NewPoint(name, tags, fields, time.Now())
	if err != nil {
		return
	}
	return pt.String(), nil
	/*
		// --------------------------------------------------------------------------------
		droppedAttributesCount := uint64(span.DroppedAttributesCount())
		attributesField := make(map[string]any)

		for _, attributes := range []pcommon.Map{resourceAttributes, scopeAttributes, span.Attributes()} {
			attributes.Range(func(k string, v pcommon.Value) bool {
				if _, found := c.spanDimensions[k]; found {
					if _, found = tags[k]; found {
						c.logger.Debug("dimension %s already exists as a tag", k)
						attributesField[k] = v.AsRaw()
					}
					tags[k] = v.AsString()
				} else {
					attributesField[k] = v.AsRaw()
				}
				return true
			})
		}
		if len(attributesField) > 0 {
			marshalledAttributes, err := json.Marshal(attributesField)
			if err != nil {
				c.logger.Debug("failed to marshal attributes to JSON", err)
				droppedAttributesCount += uint64(span.Attributes().Len())
			} else {
				fields[common.AttributeAttributes] = string(marshalledAttributes)
			}
		}

		if traceState := span.TraceState().AsRaw(); traceState != "" {
			fields[common.AttributeTraceState] = traceState
		}
		if parentSpanID := span.ParentSpanID(); !parentSpanID.IsEmpty() {
			fields[common.AttributeParentSpanID] = hex.EncodeToString(parentSpanID[:])
		}
		if name := span.Name(); name != "" {
			fields[common.AttributeSpanName] = name
		}
		if kind := span.Kind(); kind != ptrace.SpanKindUnspecified {
			fields[common.AttributeSpanKind] = kind.String()
		}

		ts := span.StartTimestamp().AsTime()
		if ts.IsZero() {
			err = errors.New("span has no timestamp")
			return
		}

		if endTime := span.EndTimestamp().AsTime(); !endTime.IsZero() {
			fields[common.AttributeEndTimeUnixNano] = endTime.UnixNano()
			fields[common.AttributeDurationNano] = endTime.Sub(ts).Nanoseconds()
		}

		droppedEventsCount := uint64(span.DroppedEventsCount())
		for i := 0; i < span.Events().Len(); i++ {
			if err = c.enqueueSpanEvent(ctx, traceID, spanID, span.Events().At(i), batch); err != nil {
				droppedEventsCount++
				c.logger.Debug("invalid span event", err)
			}
		}
		if droppedEventsCount > 0 {
			fields[common.AttributeDroppedEventsCount] = droppedEventsCount
		}

		droppedLinksCount := uint64(span.DroppedLinksCount())
		for i := 0; i < span.Links().Len(); i++ {
			if err = c.writeSpanLink(ctx, traceID, spanID, ts, span.Links().At(i), batch); err != nil {
				droppedLinksCount++
				c.logger.Debug("invalid span link", err)
			}
		}
		if droppedLinksCount > 0 {
			fields[common.AttributeDroppedLinksCount] = droppedLinksCount
		}

		status := span.Status()
		switch status.Code() {
		case ptrace.StatusCodeUnset:
		case ptrace.StatusCodeOk, ptrace.StatusCodeError:
			fields[semconv.OtelStatusCode] = status.Code().String()
		default:
			c.logger.Debug("status code not recognized", "code", status.Code())
		}
		if message := status.Message(); message != "" {
			fields[semconv.OtelStatusDescription] = message
		}

		tags[common.AttributeTraceID] = hex.EncodeToString(traceID[:])
		tags[common.AttributeSpanID] = hex.EncodeToString(spanID[:])

		for k := range tags {
			if _, found := fields[k]; found {
				c.logger.Debug("tag and field keys conflict; field will be dropped", "tag key", k)
				droppedAttributesCount++
				delete(fields, k)
			}
		}
		if droppedAttributesCount > 0 {
			fields[common.AttributeDroppedAttributesCount] = droppedAttributesCount
		}

		if err = batch.EnqueuePoint(ctx, measurement, tags, fields, ts, common.InfluxMetricValueTypeUntyped); err != nil {
			return fmt.Errorf("failed to enqueue point for span: %w", err)
		}

		return nil*/
}

func getStatus(status tracesdk.Status) string {
	switch status.Code {
	case codes.Ok, codes.Unset:
		return "ok"
	case codes.Error:
		return "error"
	}
	return "ok"
}

func getSpanType(parent trace.SpanContext) string {
	if parent.SpanID().IsValid() {
		return "entry"
	}
	if parent.SpanID().String() == "" || parent.SpanID().String() == "0000000000000000" {
		return "entry"
	}

	return "local"
}

func getSourceType(attr []attribute.KeyValue) string {
	for _, value := range attr {
		switch value.Key {
		case httpKey, rpcKey:
			return "web"
		case dbKey:
			return "db"
		case messageKey:
			return "message"
		}
	}
	return "custom"
}

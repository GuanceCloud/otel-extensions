package guance

import (
	"context"
	"errors"
	tracesdk "go.opentelemetry.io/otel/sdk/trace"
	"sync"
)

var (
	errAlreadyStarted = errors.New("already started")
)

// Exporter exports trace data in the OTLP wire format.
type Exporter struct {
	client *client

	mu      sync.RWMutex
	started bool

	startOnce sync.Once
	stopOnce  sync.Once
}

func NewExporter(ctx context.Context) *Exporter {
	exp := &Exporter{client: NewClient()}
	exp.Start(ctx)
	return exp

}

// ExportSpans exports a batch of spans.
func (e *Exporter) ExportSpans(ctx context.Context, ss []tracesdk.ReadOnlySpan) error {
	buf := ToPoint(ss)
	err := e.client.UploadTraces(ctx, buf)
	if err != nil {
		//return internal.WrapTracesError(err)
	}
	return nil
}

// Start establishes a connection to the receiving endpoint.
func (e *Exporter) Start(ctx context.Context) error {
	var err = errAlreadyStarted
	e.startOnce.Do(func() {
		e.mu.Lock()
		e.started = true
		e.mu.Unlock()
		err = e.client.Start(ctx)
	})

	return err
}

// Shutdown flushes all exports and closes all connections to the receiving endpoint.
func (e *Exporter) Shutdown(ctx context.Context) error {
	e.mu.RLock()
	started := e.started
	e.mu.RUnlock()

	if !started {
		return nil
	}

	var err error

	e.stopOnce.Do(func() {
		err = e.client.Stop(ctx)
		e.mu.Lock()
		e.started = false
		e.mu.Unlock()
	})

	return err
}

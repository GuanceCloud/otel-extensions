package guance

import (
	"context"
	"net"
	"net/http"
	"sync"
	"time"
)

var ourTransport = &http.Transport{
	Proxy: http.ProxyFromEnvironment,
	DialContext: (&net.Dialer{
		Timeout:   30 * time.Second,
		KeepAlive: 30 * time.Second,
	}).DialContext,
	ForceAttemptHTTP2:     true,
	MaxIdleConns:          100,
	IdleConnTimeout:       90 * time.Second,
	TLSHandshakeTimeout:   10 * time.Second,
	ExpectContinueTimeout: 1 * time.Second,
}

type client struct {
	name     string
	client   *http.Client
	stopCh   chan struct{}
	stopOnce sync.Once
}

// Start does nothing in a HTTP client.
func (d *client) Start(ctx context.Context) error {
	// nothing to do
	select {
	case <-ctx.Done():
		return ctx.Err()
	default:
	}
	return nil
}

// Stop shuts down the client and interrupt any in-flight request.
func (d *client) Stop(ctx context.Context) error {
	d.stopOnce.Do(func() {
		close(d.stopCh)
	})
	select {
	case <-ctx.Done():
		return ctx.Err()
	default:
	}
	return nil
}

func NewClient() *client {
	//	cfg := otlpconfig.NewHTTPConfig(asHTTPOptions(opts)...)

	httpClient := &http.Client{
		Transport: ourTransport,
		Timeout:   time.Second * 10,
	}
	//if cfg.Traces.TLSCfg != nil {
	//	transport := ourTransport.Clone()
	//	transport.TLSClientConfig = cfg.Traces.TLSCfg
	//	httpClient.Transport = transport
	//}

	stopCh := make(chan struct{})
	return &client{
		name:   "traces",
		stopCh: stopCh,
		client: httpClient,
	}
}

func (d *client) UploadTraces(ctx context.Context, lines []byte) error {
	return nil
}

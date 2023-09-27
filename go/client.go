package guance

import (
	"context"
	"net"
	"net/http"
	"sync"
	"time"
)

var (
	TRACE_CATEGORY  = "/v1/write/tracing"
	METRIC_CATEGORY = "/v1/write/metric"
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

type Client struct {
	name     string
	client   *http.Client
	stopCh   chan struct{}
	stopOnce sync.Once
}

// Start does nothing in a HTTP client.
func (d *Client) Start(ctx context.Context) error {
	// nothing to do
	select {
	case <-ctx.Done():
		return ctx.Err()
	default:
	}
	return nil
}

// Stop shuts down the client and interrupt any in-flight request.
func (d *Client) Stop(ctx context.Context) error {
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

func NewClient(endpoint string, token string) *Client {
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
	return &Client{
		name:   "traces",
		stopCh: stopCh,
		client: httpClient,
	}
}

func (d *Client) UploadTraces(ctx context.Context, lines []byte, count int) error {

	return nil
}
 
func (d *Client) UploadMetric(ctx context.Context, lines []byte) error {
	return nil
}

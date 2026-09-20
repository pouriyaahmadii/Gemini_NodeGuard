package checker

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"gemini-nodeguard/internal/types"
)

// customMockDialer returns a specific http.Client based on the node's server name.
type customMockDialer struct {
	passURL     string
	fastPassURL string
	failURL     string
}

func (d *customMockDialer) NewHTTPClient(ctx context.Context, node *types.ProxyNode) (*http.Client, func(), error) {
	client := &http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			// Rewrite target URL based on node identity
			var targetURL string
			switch node.Server {
			case "pass", "pass-again":
				targetURL = d.passURL
			case "fast-pass":
				targetURL = d.fastPassURL
			case "fail":
				targetURL = d.failURL
			default:
				targetURL = d.failURL
			}

			newReq, _ := http.NewRequestWithContext(ctx, req.Method, targetURL, req.Body)
			newReq.Header = req.Header
			return http.DefaultTransport.RoundTrip(newReq)
		}),
	}
	return client, func() {}, nil
}

func TestChecker_CheckAll(t *testing.T) {
	passServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(50 * time.Millisecond)
		w.WriteHeader(http.StatusOK)
	}))
	defer passServer.Close()

	fastPassServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(10 * time.Millisecond)
		w.WriteHeader(http.StatusOK)
	}))
	defer fastPassServer.Close()

	failServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
	}))
	defer failServer.Close()

	dialer := &customMockDialer{
		passURL:     passServer.URL,
		fastPassURL: fastPassServer.URL,
		failURL:     failServer.URL,
	}

	checker := NewChecker(CheckOptions{
		Concurrency: 2,
		Timeout:     2 * time.Second,
		TargetURLs:  []string{"http://dummy.url"},
	}, dialer)

	nodes := []*types.ProxyNode{
		{ID: "node1", Server: "pass"},
		{ID: "node2", Server: "fast-pass"},
		{ID: "node3", Server: "fail"},
		{ID: "node4", Server: "pass-again"},
	}

	result, general := checker.CheckAll(context.Background(), nodes)

	if len(result) != 3 {
		t.Fatalf("expected 3 compatible nodes, got %d", len(result))
	}

	if len(general) != 1 {
		t.Fatalf("expected 1 general node, got %d", len(general))
	}

	// Verify filtering: node3 should not be in the compatible result
	for _, n := range result {
		if n.ID == "node3" {
			t.Errorf("node3 should have been filtered out of compatible nodes")
		}
	}

	// Verify sorting by latency: node2 (fast-pass) should be first
	if result[0].ID != "node2" {
		t.Errorf("expected node2 to be first (fastest), got %s", result[0].ID)
	}

	// Verify all returned nodes are marked compatible
	for _, n := range result {
		if !n.IsGeminiCompatible {
			t.Errorf("expected node %s to be compatible", n.ID)
		}
	}

	// Verify general node
	if general[0].ID != "node3" {
		t.Errorf("expected node3 in general nodes, got %s", general[0].ID)
	}
	if general[0].IsGeminiCompatible {
		t.Errorf("expected general node to not be Gemini compatible")
	}
	if !general[0].IsAlive {
		t.Errorf("expected general node to be alive")
	}
}

func TestChecker_ContextCancellation(t *testing.T) {
	slowServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(2 * time.Second)
		w.WriteHeader(http.StatusOK)
	}))
	defer slowServer.Close()

	dialer := &customMockDialer{
		passURL:     slowServer.URL,
		fastPassURL: slowServer.URL,
		failURL:     slowServer.URL,
	}

	checker := NewChecker(CheckOptions{
		Concurrency: 2,
		Timeout:     5 * time.Second,
		TargetURLs:  []string{"http://dummy.url"},
	}, dialer)

	nodes := []*types.ProxyNode{
		{ID: "node1", Server: "pass"},
		{ID: "node2", Server: "pass"},
	}

	ctx, cancel := context.WithCancel(context.Background())

	// Cancel the context almost immediately
	go func() {
		time.Sleep(50 * time.Millisecond)
		cancel()
	}()

	start := time.Now()
	result, general := checker.CheckAll(ctx, nodes)
	duration := time.Since(start)

	if duration > 1*time.Second {
		t.Errorf("expected CheckAll to return quickly on cancel, took %v", duration)
	}

	if len(result) != 0 {
		t.Errorf("expected no results due to cancellation, got %d", len(result))
	}

	if len(general) != 0 {
		t.Errorf("expected no general results due to cancellation, got %d", len(general))
	}
}

type roundTripFunc func(req *http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req)
}

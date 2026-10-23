package checker

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"gemini-nodeguard/internal/registry"
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

			newReq, _ := http.NewRequestWithContext(req.Context(), req.Method, targetURL, req.Body)
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

	// Add search service rules so that it evaluates correctly during the test
	registry.GetServices()["search"].StatusRules = []registry.StatusRule{
		{Status: []int{200}, Classification: types.ClassificationConfirmedCompatible},
	}
	checker := NewChecker(CheckOptions{
		Concurrency:         2,
		ProcessStartTimeout: 5 * time.Second,
		ProbeTimeout:        8 * time.Second,
		NodeTimeout:         2 * time.Second,
		ConfirmCount:        1,
		Services:            []string{"search"},
	}, dialer)

	nodes := []*types.ProxyNode{
		{ID: "node1", Server: "pass", ServiceResults: make(map[string]types.ServiceResult)},
		{ID: "node2", Server: "fast-pass", ServiceResults: make(map[string]types.ServiceResult)},
		{ID: "node3", Server: "fail", ServiceResults: make(map[string]types.ServiceResult)},
		{ID: "node4", Server: "pass-again", ServiceResults: make(map[string]types.ServiceResult)},
	}

	result := checker.CheckAll(context.Background(), nodes)

	if len(result) != 4 {
		t.Fatalf("expected 4 returned nodes, got %d", len(result))
	}

	// node2 is fast-pass, node1 and node4 are pass, node3 is fail (general transport alive)
	var compatibleCount int
	for _, n := range result {
		res := n.ServiceResults["search"]
		if res.Classification == types.ClassificationConfirmedCompatible {
			compatibleCount++
		}
	}

	if compatibleCount != 3 {
		for _, n := range result {
			t.Logf("Node %s classification: %s, diag: %s, HTTP: %d", n.ID, n.ServiceResults["search"].Classification, n.ServiceResults["search"].Diagnostic, n.ServiceResults["search"].HTTPStatus)
		}
		t.Errorf("Expected 3 nodes to be compatible with search, got %d", compatibleCount)
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
		Concurrency:         2,
		ProcessStartTimeout: 5 * time.Second,
		ProbeTimeout:        8 * time.Second,
		NodeTimeout:         5 * time.Second,
		ConfirmCount:        1,
		Services:            []string{"search"},
	}, dialer)

	nodes := []*types.ProxyNode{
		{ID: "node1", Server: "pass", ServiceResults: make(map[string]types.ServiceResult)},
		{ID: "node2", Server: "pass", ServiceResults: make(map[string]types.ServiceResult)},
	}

	ctx, cancel := context.WithCancel(context.Background())

	// Cancel the context almost immediately
	go func() {
		time.Sleep(50 * time.Millisecond)
		cancel()
	}()

	start := time.Now()
	result := checker.CheckAll(ctx, nodes)
	duration := time.Since(start)

	if duration > 1*time.Second {
		t.Errorf("expected CheckAll to return quickly on cancel, took %v", duration)
	}

	if result[0].IsAlive || result[1].IsAlive {
		t.Errorf("expected nodes to not be alive due to context cancellation")
	}
}

type roundTripFunc func(req *http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req)
}

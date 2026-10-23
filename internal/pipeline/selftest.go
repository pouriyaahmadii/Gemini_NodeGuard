package pipeline

import (
	"context"
	"fmt"
	"log"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"time"

	"gemini-nodeguard/internal/checker"
	"gemini-nodeguard/internal/types"
)

type FakeMockDialer struct {
	Client *http.Client
}

func (d *FakeMockDialer) NewHTTPClient(ctx context.Context, node *types.ProxyNode) (*http.Client, func(), error) {
	if d.Client == nil {
		d.Client = &http.Client{
			CheckRedirect: func(req *http.Request, via []*http.Request) error {
				return http.ErrUseLastResponse
			},
		}
	} else {
		// Just ensure check redirect is configured
		d.Client.CheckRedirect = func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		}
	}
	return d.Client, func() {}, nil
}

// RunSelftest bootstraps an entirely contained test environment, executing the full pipeline
// against mock subscription endpoints and in-process dummy nodes to verify end-to-end functionality.
func RunSelftest() int {
	log.Println("--- STARTING E2E SELFTEST ---")

	// 1. Subscription Server (httptest)
	subData := "vmess://eyJ2IjoiMiIsImFkZCI6InBhc3MiLCJwb3J0IjoiNDQzIiwiaWQiOiJ1dWlkIiwicHMiOiJwYXNzIn0=\n" +
		"vless://uuid@fail:443?type=tcp#fail"

	subServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(subData))
	}))
	defer subServer.Close()

	// 2. Real Prefilter endpoints (so TCP prefilter passes for 'pass' node)
	passListener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		log.Printf("selftest failed to listen on localhost: %v", err)
		return 1
	}
	defer passListener.Close()
	passPort := passListener.Addr().(*net.TCPAddr).Port

	// Overwrite the sub data with actual local ports so prefilter works
	subDataReplaced := fmt.Sprintf("vless://uuid@127.0.0.1:%d?type=tcp#pass\nvless://uuid@127.0.0.1:1?type=tcp#fail", passPort)

	subServer2 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(subDataReplaced))
	}))
	defer subServer2.Close()

	// 3. Mock Service Probe endpoints
	probeServer := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK) // Always return 200 for mock probes
	}))
	defer probeServer.Close()

	// Create client that routes there directly ignoring actual hostname
	mockClient := probeServer.Client()
	originalTransport := mockClient.Transport.(*http.Transport)
	originalTransport.TLSClientConfig.InsecureSkipVerify = true
	originalTransport.DialContext = func(ctx context.Context, network, addr string) (net.Conn, error) {
		var dialer net.Dialer
		return dialer.DialContext(ctx, network, probeServer.Listener.Addr().String())
	}

	mockDialer := &FakeMockDialer{Client: mockClient}

	// 4. Create output dir
	outDir, _ := os.MkdirTemp("", "nodeguard_selftest_*")
	defer os.RemoveAll(outDir)

	reportPath := filepath.Join(outDir, "report.json")

	// 5. Build Pipeline Config
	cfg := PipelineConfig{
		SubURLs:     []string{subServer2.URL},
		Services:    []string{"search"},
		OutputDir:   outDir,
		ReportPath:  reportPath,
		Concurrency: 2,
		Timeout:     5 * time.Second,
		Dialers:     []checker.NodeDialer{mockDialer},
		Prefilter:   true, // Ensure prefilter is on to test it
	}

	// 6. Run Pipeline
	exitCode, err := RunPipeline(context.Background(), cfg)
	if err != nil {
		log.Printf("Selftest pipeline returned error: %v", err)
		return exitCode
	}

	// 7. Verify outputs

	if _, err := os.Stat(filepath.Join(outDir, "search-sub.txt")); os.IsNotExist(err) {
		log.Printf("Selftest failed: search-sub.txt was not created")
		return 1
	}

	if _, err := os.Stat(reportPath); os.IsNotExist(err) {
		log.Printf("Selftest failed: report.json was not created")
		return 1
	}

	log.Println("--- SELFTEST PASSED ---")
	return 0
}

package pipeline

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"gemini-nodeguard/internal/checker"
)

func TestRunPipeline(t *testing.T) {
	subServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("vmess://eyJ2IjoiMiIsImFkZCI6IjEyNy4wLjAuMSIsInBvcnQiOiIxMTAwIiwiaWQiOiJ1dWlkIiwicHMiOiJwYXNzIn0=\n"))
	}))
	defer subServer.Close()

	outDir := t.TempDir()
	reportPath := filepath.Join(outDir, "report.json")

	cfg := PipelineConfig{
		SubURLs:     []string{subServer.URL},
		Services:    []string{"search"},
		OutputDir:   outDir,
		ReportPath:  reportPath,
		Concurrency: 2,
		Timeout:     5 * time.Second,
		Dialers:     []checker.NodeDialer{&FakeMockDialer{}},
		Prefilter:   false,
	}

	exitCode, err := RunPipeline(context.Background(), cfg)
	if err != nil {
		t.Fatalf("RunPipeline returned error: %v", err)
	}

	if exitCode != 0 {
		t.Errorf("Expected exit code 0, got %d", exitCode)
	}

	if _, err := os.Stat(reportPath); os.IsNotExist(err) {
		t.Errorf("report.json was not created")
	}
}

func TestRunPipeline_FetchError(t *testing.T) {
	cfg := PipelineConfig{
		SubURLs: []string{"http://localhost:1100/invalid"}, // this will fail fast (connection refused)
		Timeout: 1 * time.Second,
	}

	exitCode, err := RunPipeline(context.Background(), cfg)
	if err == nil {
		t.Errorf("Expected error for unreachable fetch")
	}
	if exitCode != 1 {
		t.Errorf("Expected exit code 1 for fetch error, got %d", exitCode)
	}
}

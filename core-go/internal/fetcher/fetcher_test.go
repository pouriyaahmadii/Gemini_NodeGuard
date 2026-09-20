package fetcher

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestFetcher_Fetch(t *testing.T) {
	t.Run("Successful Fetch", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Header.Get("User-Agent") != "v2rayN/6.23" {
				t.Errorf("Expected User-Agent v2rayN/6.23, got %s", r.Header.Get("User-Agent"))
			}
			w.WriteHeader(http.StatusOK)
			// dmxlc3M6Ly90ZXN0 = vless://test
			w.Write([]byte("dmxlc3M6Ly90ZXN0"))
		}))
		defer ts.Close()

		fetcher := NewFetcher(FetchOptions{})
		lines, err := fetcher.Fetch(context.Background(), ts.URL)
		if err != nil {
			t.Fatalf("Expected no error, got %v", err)
		}
		if len(lines) != 1 || lines[0] != "vless://test" {
			t.Fatalf("Expected ['vless://test'], got %v", lines)
		}
	})

	t.Run("HTTP Error 500", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer ts.Close()

		fetcher := NewFetcher(FetchOptions{})
		_, err := fetcher.Fetch(context.Background(), ts.URL)
		if err == nil || !strings.Contains(err.Error(), "unexpected status code: 500") {
			t.Fatalf("Expected status code error, got %v", err)
		}
	})

	t.Run("Context Timeout", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			time.Sleep(100 * time.Millisecond)
			w.WriteHeader(http.StatusOK)
		}))
		defer ts.Close()

		fetcher := NewFetcher(FetchOptions{})
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Millisecond)
		defer cancel()
		_, err := fetcher.Fetch(ctx, ts.URL)
		if err == nil {
			t.Fatal("Expected context deadline exceeded error")
		}
	})

	t.Run("Payload Truncation (MaxBytes)", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusOK)
			// Output string that triggers known plaintext detection but exceeds maxbytes
			w.Write([]byte(strings.Repeat("vless://test", 10)))
		}))
		defer ts.Close()

		fetcher := NewFetcher(FetchOptions{MaxBytes: 10}) // Limit to 10 bytes
		lines, err := fetcher.Fetch(context.Background(), ts.URL)
		if err != nil {
			t.Fatalf("Expected no error, got %v", err)
		}

		// Expected truncated to 10 bytes: "vless://te"
		if len(lines) != 1 || lines[0] != "vless://te" {
			t.Fatalf("Expected string truncated to 10 chars 'vless://te', got %v (len %d)", lines, len(lines[0]))
		}
	})
}

func TestFetcher_FetchAll(t *testing.T) {
	t.Run("FetchAll Success with Deduplication", func(t *testing.T) {
		ts1 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Write([]byte("vless://node1\nvmess://node2"))
		}))
		defer ts1.Close()

		ts2 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Write([]byte("vmess://node2\ntrojan://node3"))
		}))
		defer ts2.Close()

		fetcher := NewFetcher(FetchOptions{})
		lines, err := fetcher.FetchAll(context.Background(), []string{ts1.URL, ts2.URL})
		if err != nil {
			t.Fatalf("Expected no error, got %v", err)
		}

		expected := []string{"vless://node1", "vmess://node2", "trojan://node3"}
		if len(lines) != len(expected) {
			t.Fatalf("Expected length %d, got %d", len(expected), len(lines))
		}
		for i, v := range expected {
			if lines[i] != v {
				t.Fatalf("Expected %s at index %d, got %s", v, i, lines[i])
			}
		}
	})

	t.Run("FetchAll Partial Success", func(t *testing.T) {
		tsSuccess := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Write([]byte("vless://node1"))
		}))
		defer tsSuccess.Close()

		tsFail := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer tsFail.Close()

		fetcher := NewFetcher(FetchOptions{})
		lines, err := fetcher.FetchAll(context.Background(), []string{tsSuccess.URL, tsFail.URL})

		if err != nil {
			t.Fatalf("Expected no error for partial success, got %v", err)
		}
		if len(lines) != 1 || lines[0] != "vless://node1" {
			t.Fatalf("Expected single success line, got %v", lines)
		}
	})

	t.Run("FetchAll Complete Failure", func(t *testing.T) {
		tsFail := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer tsFail.Close()

		fetcher := NewFetcher(FetchOptions{})
		lines, err := fetcher.FetchAll(context.Background(), []string{tsFail.URL, tsFail.URL})

		if err == nil {
			t.Fatal("Expected error for complete failure")
		}
		if len(lines) != 0 {
			t.Fatalf("Expected empty lines, got %v", lines)
		}
	})

	t.Run("Empty URLs", func(t *testing.T) {
		fetcher := NewFetcher(FetchOptions{})
		lines, err := fetcher.FetchAll(context.Background(), []string{})

		if err != nil {
			t.Fatalf("Expected no error, got %v", err)
		}
		if len(lines) != 0 {
			t.Fatalf("Expected empty lines, got %v", lines)
		}
	})
}

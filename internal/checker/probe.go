package checker

import (
	"context"
	"net/http"
	"time"

	"gemini-sub-checker/internal/types"
)

const (
	userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
)

// Probe executes an HTTP GET request to check Gemini compatibility using the provided client.
func Probe(ctx context.Context, client *http.Client, node *types.ProxyNode, targetURL string) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		node.IsGeminiCompatible = false
		return
	}

	req.Header.Set("User-Agent", userAgent)

	start := time.Now()
	resp, err := client.Do(req)
	node.Latency = time.Since(start)

	if err != nil {
		// Network reset, TLS failure, timeout, etc.
		node.IsAlive = false
		node.IsGeminiCompatible = false
		return
	}
	defer resp.Body.Close()

	// We got an HTTP response, meaning proxy transport works
	node.IsAlive = true

	// Check response status
	if resp.StatusCode == http.StatusOK || (resp.StatusCode >= 300 && resp.StatusCode < 400) {
		node.IsGeminiCompatible = true
	} else {
		// 403 (geoblock), 500, etc.
		node.IsGeminiCompatible = false
	}
}

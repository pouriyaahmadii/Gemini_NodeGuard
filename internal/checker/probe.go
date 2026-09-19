package checker

import (
	"bytes"
	"context"
	"io"
	"log"
	"net/http"
	"time"

	"gemini-sub-checker/internal/types"
)

var geoblockKeywords = [][]byte{
	[]byte("isn't currently supported in your country"),
	[]byte("is not currently supported in your country"),
	[]byte("not currently supported in your country"),
	[]byte("not yet available in your region"),
	[]byte("is not yet available in your region"),
	[]byte("is not available in your region"),
	[]byte("is not available in your country"),
	[]byte("not available in your country"),
	[]byte("unsupported country"),
	[]byte("unsupported region"),
	[]byte("not available in your location"),
	[]byte("not supported in your country"),
	[]byte("user location is not supported"),
	[]byte("failed_precondition"),
}

// Probe executes an HTTP GET request to check Gemini compatibility using the provided client.
func Probe(ctx context.Context, client *http.Client, node *types.ProxyNode, targetURL string) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		node.IsGeminiCompatible = false
		return
	}

	req.Header.Set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
	req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Sec-Ch-Ua", `"Chromium";v="128", "Not;A=Brand";v="24", "Google Chrome";v="128"`)
	req.Header.Set("Sec-Ch-Ua-Mobile", "?0")
	req.Header.Set("Sec-Ch-Ua-Platform", `"macOS"`)

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

	// If the status is forbidden, too many requests or server error, assume incompatible
	if resp.StatusCode == http.StatusForbidden || resp.StatusCode == http.StatusTooManyRequests || resp.StatusCode >= 500 {
		node.IsGeminiCompatible = false
		log.Printf("[Probe] %s: incompatible due to status code %d from %s", node.Server, resp.StatusCode, targetURL)
		return
	}

	// For web UI endpoints, check response status for 200 OK or redirect
	if resp.StatusCode == http.StatusOK || (resp.StatusCode >= 300 && resp.StatusCode < 400) {
		// High-performance body inspection
		buf := make([]byte, 256*1024)
		n, err := io.ReadFull(io.LimitReader(resp.Body, 256*1024), buf)
		if err != nil && err != io.ErrUnexpectedEOF && err != io.EOF {
			// Read error, might be a broken connection during transfer
			node.IsGeminiCompatible = false
			log.Printf("[Probe] %s: read body error from %s: %v", node.Server, targetURL, err)
			return
		}

		bodyBuf := bytes.ToLower(buf[:n])

		for _, kw := range geoblockKeywords {
			if bytes.Contains(bodyBuf, kw) {
				node.IsGeminiCompatible = false
				log.Printf("[Probe] %s: geoblocked keyword found for %s", node.Server, targetURL)
				return
			}
		}

		node.IsGeminiCompatible = true
	} else {
		node.IsGeminiCompatible = false
		log.Printf("[Probe] %s: unexpected status code %d from %s", node.Server, resp.StatusCode, targetURL)
	}
}

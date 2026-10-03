package checker

import (
	"bytes"
	"context"
	"io"
	"log"
	"net/http"
	"strings"
	"time"

	"gemini-nodeguard/internal/types"
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
	[]byte("country or region"),
	[]byte("not supported in your region"),
	[]byte("geographic restriction"),
	[]byte("access denied"),
	[]byte("your client does not have permission"),
	[]byte("that's an error"),
	[]byte("that’s an error"),
}

// ProbeResult contains the outcome of a single probe.
type ProbeResult struct {
	Compatible bool
	IsAlive    bool
	Latency    time.Duration
}

// Probe executes an HTTP GET request to check endpoint compatibility using the provided client.
// It includes 1 automatic retry for transient errors.
func Probe(ctx context.Context, client *http.Client, node *types.ProxyNode, targetURL string) ProbeResult {
	var result ProbeResult
	var err error
	var resp *http.Response
	var start time.Time

	for attempt := 0; attempt < 2; attempt++ {
		if attempt > 0 {
			// Backoff before retry
			select {
			case <-ctx.Done():
				return result
			case <-time.After(500 * time.Millisecond):
			}
		}

		req, reqErr := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
		if reqErr != nil {
			return result
		}

		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
		req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
		req.Header.Set("Accept-Language", "en-US,en;q=0.9")
		req.Header.Set("Sec-Ch-Ua", `"Chromium";v="128", "Not;A=Brand";v="24", "Google Chrome";v="128"`)
		req.Header.Set("Sec-Ch-Ua-Mobile", "?0")
		req.Header.Set("Sec-Ch-Ua-Platform", `"Windows"`)
		req.Header.Set("Sec-Fetch-Dest", "document")
		req.Header.Set("Sec-Fetch-Mode", "navigate")
		req.Header.Set("Sec-Fetch-Site", "none")
		req.Header.Set("Upgrade-Insecure-Requests", "1")

		start = time.Now()
		resp, err = client.Do(req)
		if err == nil {
			break // Success, don't retry
		}
	}

	result.Latency = time.Since(start)

	if err != nil {
		// Network reset, TLS failure, timeout, etc.
		result.IsAlive = false
		result.Compatible = false
		return result
	}
	defer resp.Body.Close()

	// We got an HTTP response, meaning proxy transport works
	result.IsAlive = true

	// High-performance body inspection
	bodyBytes, readErr := io.ReadAll(io.LimitReader(resp.Body, 256*1024))
	if readErr != nil && readErr != io.ErrUnexpectedEOF && readErr != io.EOF {
		// Read error, might be a broken connection during transfer
		result.Compatible = false
		log.Printf("[Probe] %s: read body error from %s: %v", node.Server, targetURL, readErr)
		return result
	}

	lowerBody := strings.ToLower(string(bodyBytes))
	bodyBuf := []byte(lowerBody) // Keep bodyBuf for geoblockKeywords which is [][]byte

	isAPI := strings.Contains(targetURL, "generativelanguage.googleapis.com")

	if isAPI {
		// For API endpoints, we have specific acceptance criteria based on user requirements.
		// Accept HTTP 403 as COMPATIBLE if the JSON body contains "permission_denied", "unregistered", or "method doesn't allow".
		// Accept HTTP 400 as COMPATIBLE if the JSON body contains "invalid_argument" or "api key not valid".
		if resp.StatusCode == http.StatusForbidden { // HTTP 403
			if strings.Contains(lowerBody, "permission_denied") ||
				strings.Contains(lowerBody, "unregistered") ||
				strings.Contains(lowerBody, "method doesn't allow") {
				// Positive confirmation that API is reachable
				// Check for geoblock keywords
				for _, kw := range geoblockKeywords {
					if bytes.Contains(bodyBuf, kw) {
						result.Compatible = false
						log.Printf("[Probe] %s: API endpoint returned geoblocked keyword for %s", node.Server, targetURL)
						return result
					}
				}
				result.Compatible = true
				return result
			}
		} else if resp.StatusCode == http.StatusBadRequest { // HTTP 400
			if strings.Contains(lowerBody, "invalid_argument") ||
				strings.Contains(lowerBody, "api key not valid") ||
				strings.Contains(lowerBody, "api key") {
				for _, kw := range geoblockKeywords {
					if bytes.Contains(bodyBuf, kw) {
						result.Compatible = false
						log.Printf("[Probe] %s: API endpoint returned geoblocked keyword for %s", node.Server, targetURL)
						return result
					}
				}
				result.Compatible = true
				return result
			}
		} else if resp.StatusCode == http.StatusOK {
			result.Compatible = true
			return result
		}

		result.Compatible = false
		log.Printf("[Probe] %s: API endpoint incompatible (status: %d, body: %q)", node.Server, resp.StatusCode, string(bodyBytes))
		return result
	}

	// For web endpoints (gemini.google.com, jules.google.com)
	// If the status is forbidden (403), it's incompatible. Same for 429 or 500+
	if resp.StatusCode == http.StatusForbidden || resp.StatusCode == http.StatusTooManyRequests || resp.StatusCode >= 500 {
		result.Compatible = false
		log.Printf("[Probe] %s: web endpoint incompatible due to status code %d from %s", node.Server, resp.StatusCode, targetURL)
		return result
	}

	// For web UI endpoints, check response status for 200 OK or redirect (3xx)
	if resp.StatusCode == http.StatusOK || (resp.StatusCode >= 300 && resp.StatusCode < 400) {
		for _, kw := range geoblockKeywords {
			if bytes.Contains(bodyBuf, kw) {
				result.Compatible = false
				log.Printf("[Probe] %s: geoblocked keyword found for %s", node.Server, targetURL)
				return result
			}
		}
		result.Compatible = true
	} else {
		result.Compatible = false
		log.Printf("[Probe] %s: unexpected status code %d from %s", node.Server, resp.StatusCode, targetURL)
	}

	return result
}

package checker

import (
	"context"
	"io"
	"log"
	"net/http"
	"time"

	"gemini-nodeguard/internal/registry"
	"gemini-nodeguard/internal/types"
)

// Probe executes an HTTP GET request to check service compatibility using the provided client.
// It uses the definitions from the registry to evaluate the outcome.
func Probe(ctx context.Context, client *http.Client, node *types.ProxyNode, serviceDef *registry.ServiceDefinition) types.ServiceResult {
	var result types.ServiceResult
	var err error
	var resp *http.Response
	var start time.Time

	for attempt := 0; attempt < 2; attempt++ {
		if attempt > 0 {
			// Backoff before retry
			select {
			case <-ctx.Done():
				result.Classification = types.ClassificationTransportError
				result.Diagnostic = "context canceled during retry backoff"
				return result
			case <-time.After(500 * time.Millisecond):
			}
		}

		req, reqErr := http.NewRequestWithContext(ctx, serviceDef.Method, serviceDef.Endpoint, nil)
		if reqErr != nil {
			result.Classification = types.ClassificationTransportError
			result.Diagnostic = reqErr.Error()
			return result
		}

		// Realistic browser headers to avoid trivial blocks
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
		result.Classification = types.ClassificationTransportError
		result.Diagnostic = err.Error()
		return result
	}
	defer resp.Body.Close()

	result.HTTPStatus = resp.StatusCode

	// High-performance bounded body inspection (max 256KB)
	bodyBytes, readErr := io.ReadAll(io.LimitReader(resp.Body, 256*1024))
	if readErr != nil && readErr != io.ErrUnexpectedEOF && readErr != io.EOF {
		result.Classification = types.ClassificationTransportError
		result.Diagnostic = "failed to read response body: " + readErr.Error()
		log.Printf("[Probe] %s: read body error from %s: %v", node.Server, serviceDef.Endpoint, readErr)
		return result
	}

	// Use the service's custom evaluator
	result.Classification = serviceDef.EvaluateResponse(resp, bodyBytes)

	if result.Classification != types.ClassificationConfirmedCompatible {
		result.Diagnostic = "Endpoint evaluated as " + string(result.Classification)
	}

	return result
}

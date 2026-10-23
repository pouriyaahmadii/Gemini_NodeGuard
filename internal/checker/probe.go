package checker

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"gemini-nodeguard/internal/registry"
	"gemini-nodeguard/internal/types"
)

// Probe executes an HTTP request to check service compatibility using the provided client.
// It implements manual redirect following (up to 4 hops) and evaluates the responses using the registry definitions.
func Probe(ctx context.Context, client *http.Client, node *types.ProxyNode, serviceDef *registry.ServiceDefinition) types.ServiceResult {
	var result types.ServiceResult
	var start time.Time
	var attemptErr error
	var finalResp *http.Response
	var finalBody []byte

	// Keep track of the request URL across redirects
	currentURL := serviceDef.Endpoint

	// Use custom CheckRedirect that prevents automatic following
	originalCheckRedirect := client.CheckRedirect
	client.CheckRedirect = func(req *http.Request, via []*http.Request) error {
		return http.ErrUseLastResponse
	}
	defer func() { client.CheckRedirect = originalCheckRedirect }()

	for attempt := 0; attempt < 2; attempt++ {
		if attempt > 0 {
			// Jittered backoff: 800ms
			select {
			case <-ctx.Done():
				result.Classification = types.ClassificationTransportError
				result.Diagnostic = "context canceled during retry backoff"
				return result
			case <-time.After(800 * time.Millisecond):
			}
		}

		start = time.Now()
		currentURL = serviceDef.Endpoint
		redirectCount := 0
		var resp *http.Response
		var bodyBytes []byte

		for redirectCount <= 4 {
			var bodyReader io.Reader
			if serviceDef.Body != "" {
				bodyReader = strings.NewReader(serviceDef.Body)
			}

			req, reqErr := http.NewRequestWithContext(ctx, serviceDef.GetMethod(), currentURL, bodyReader)
			if reqErr != nil {
				attemptErr = reqErr
				break
			}

			// Realistic browser headers
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

			for k, v := range serviceDef.Headers {
				req.Header.Set(k, v)
			}

			resp, attemptErr = client.Do(req)
			if attemptErr != nil {
				break
			}

			// Read body up to 256KB safely
			bodyBytes, attemptErr = readBodySafely(resp.Body)
			resp.Body.Close()
			if attemptErr != nil {
				break
			}

			// Check for redirects
			if resp.StatusCode >= 300 && resp.StatusCode < 400 && serviceDef.GetFollowRedirects() {
				// Evaluate intermediate redirect using the registry logic
				class := serviceDef.EvaluateResponse(resp.StatusCode, currentURL, bodyBytes)
				// If a rule specifically matched this redirect and gave a definitive answer (not inconclusive), stop here
				if class != types.ClassificationInconclusive && class != types.ClassificationConfirmedCompatible {
					finalResp = resp
					finalBody = bodyBytes
					break
				}

				loc := resp.Header.Get("Location")
				if loc == "" {
					break // No location to follow
				}

				parsedLoc, err := url.Parse(loc)
				if err != nil {
					attemptErr = fmt.Errorf("invalid redirect location: %w", err)
					break
				}

				base, _ := url.Parse(currentURL)
				currentURL = base.ResolveReference(parsedLoc).String()

				redirectCount++
				continue
			}

			// No redirect or max redirects reached
			finalResp = resp
			finalBody = bodyBytes
			break
		}

		if attemptErr == nil && finalResp != nil {
			// Success! Stop retrying
			break
		}
	}

	result.Latency = time.Since(start)

	if attemptErr != nil {
		result.Classification = types.ClassificationTransportError
		result.Diagnostic = attemptErr.Error()
		return result
	}

	if finalResp == nil {
		result.Classification = types.ClassificationTransportError
		result.Diagnostic = "too many redirects or empty response"
		return result
	}

	result.HTTPStatus = finalResp.StatusCode

	// Evaluate final response
	result.Classification = serviceDef.EvaluateResponse(finalResp.StatusCode, currentURL, finalBody)

	if result.Classification != types.ClassificationConfirmedCompatible {
		if result.Diagnostic == "" {
			result.Diagnostic = fmt.Sprintf("Endpoint %s evaluated as %s (HTTP %d)", currentURL, string(result.Classification), result.HTTPStatus)
		}
	}

	return result
}

func readBodySafely(r io.Reader) ([]byte, error) {
	b, err := io.ReadAll(io.LimitReader(r, 256*1024))
	if err != nil && err != io.ErrUnexpectedEOF && err != io.EOF {
		return nil, err
	}
	return b, nil
}

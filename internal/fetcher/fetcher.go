package fetcher

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"sync"
	"time"
)

// FetchOptions defines configuration for the Fetcher.
type FetchOptions struct {
	Timeout   time.Duration
	UserAgent string
	MaxBytes  int64
}

// Fetcher handles retrieving subscription contents over HTTP/HTTPS.
type Fetcher struct {
	client  *http.Client
	options FetchOptions
}

// NewFetcher creates a new Fetcher with the given options.
// It applies default values if options are zeroed.
func NewFetcher(opts FetchOptions) *Fetcher {
	if opts.Timeout <= 0 {
		opts.Timeout = 15 * time.Second
	}
	if opts.UserAgent == "" {
		opts.UserAgent = "v2rayN/6.23"
	}
	if opts.MaxBytes <= 0 {
		opts.MaxBytes = 10 * 1024 * 1024 // 10MB
	}

	return &Fetcher{
		client: &http.Client{
			Timeout: opts.Timeout,
		},
		options: opts,
	}
}

// Fetch retrieves subscription contents from a single URL.
func (f *Fetcher) Fetch(ctx context.Context, subURL string) ([]string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, subURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("User-Agent", f.options.UserAgent)

	resp, err := f.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to execute request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}

	// Read with max bytes limit
	limitReader := io.LimitReader(resp.Body, f.options.MaxBytes)
	bodyBytes, err := io.ReadAll(limitReader)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	return DecodeSubscription(bodyBytes)
}

// FetchAll retrieves subscription contents from multiple URLs concurrently.
func (f *Fetcher) FetchAll(ctx context.Context, urls []string) ([]string, error) {
	if len(urls) == 0 {
		return []string{}, nil
	}

	type result struct {
		index int
		lines []string
		err   error
		url   string
	}

	results := make([]result, len(urls))
	var wg sync.WaitGroup

	// Bounded concurrency semaphore (max 5 concurrent requests)
	semaphore := make(chan struct{}, 5)

	for i, u := range urls {
		wg.Add(1)
		go func(index int, subURL string) {
			defer wg.Done()

			// Acquire semaphore
			semaphore <- struct{}{}
			defer func() { <-semaphore }()

			lines, err := f.Fetch(ctx, subURL)
			results[index] = result{
				index: index,
				lines: lines,
				err:   err,
				url:   subURL,
			}
		}(i, u)
	}

	wg.Wait()

	var allLines []string
	var errs []error
	seen := make(map[string]bool)
	successCount := 0

	for _, res := range results {
		if res.err != nil {
			errs = append(errs, fmt.Errorf("fetch failed for %s: %w", res.url, res.err))
			log.Printf("Fetch failed for %s: %v", res.url, res.err)
			continue
		}

		successCount++
		for _, line := range res.lines {
			if !seen[line] {
				seen[line] = true
				allLines = append(allLines, line)
			}
		}
	}

	if successCount > 0 {
		return allLines, nil
	}

	return nil, errors.Join(errs...)
}

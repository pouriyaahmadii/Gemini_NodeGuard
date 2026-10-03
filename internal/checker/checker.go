package checker

import (
	"context"
	"sort"
	"sync"
	"time"

	"gemini-nodeguard/internal/types"
)

// CheckOptions configures the behavior of the checker pool.
type CheckOptions struct {
	Concurrency         int
	Timeout             time.Duration
	CoreTargetURLs      []string
	SecondaryTargetURLs []string
}

// DefaultCheckOptions returns recommended default settings.
func DefaultCheckOptions() CheckOptions {
	return CheckOptions{
		Concurrency: 10,
		Timeout:     8 * time.Second,
		CoreTargetURLs: []string{
			"https://gemini.google.com",
			"https://aistudio.google.com",
			"https://notebooklm.google.com",
		},
		SecondaryTargetURLs: []string{
			"https://generativelanguage.googleapis.com/v1beta/models",
			"https://jules.google.com",
		},
	}
}

// Checker orchestrates the concurrent testing of ProxyNodes.
type Checker struct {
	options CheckOptions
	dialers []NodeDialer
}

// NewChecker initializes a new Checker with given options and dialers.
func NewChecker(opts CheckOptions, dialers ...NodeDialer) *Checker {
	if opts.Concurrency <= 0 {
		opts.Concurrency = 10
	}
	if opts.Timeout <= 0 {
		opts.Timeout = 8 * time.Second
	}
	if len(opts.CoreTargetURLs) == 0 {
		opts.CoreTargetURLs = []string{
			"https://gemini.google.com",
			"https://aistudio.google.com",
			"https://notebooklm.google.com",
		}
	}
	if len(opts.SecondaryTargetURLs) == 0 {
		opts.SecondaryTargetURLs = []string{
			"https://generativelanguage.googleapis.com/v1beta/models",
			"https://jules.google.com",
		}
	}
	return &Checker{
		options: opts,
		dialers: dialers,
	}
}

// CheckAll executes the checking process across all provided nodes,
// and returns filtered and sorted slices of Gemini compatible and general nodes.
func (c *Checker) CheckAll(ctx context.Context, nodes []*types.ProxyNode) ([]*types.ProxyNode, []*types.ProxyNode) {
	workQueue := make(chan *types.ProxyNode, len(nodes))
	var wg sync.WaitGroup

	// Enqueue jobs
	for _, n := range nodes {
		workQueue <- n
	}
	close(workQueue)

	// Start workers
	for i := 0; i < c.options.Concurrency; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for {
				select {
				case <-ctx.Done():
					return
				case node, ok := <-workQueue:
					if !ok {
						return
					}
					c.processNode(ctx, node)
				}
			}
		}()
	}

	wg.Wait()

	// Filter and sort results
	var compatible []*types.ProxyNode
	var general []*types.ProxyNode
	for _, n := range nodes {
		if n.IsGeminiCompatible {
			compatible = append(compatible, n)
		} else if n.IsAlive {
			general = append(general, n)
		}
	}

	sort.SliceStable(compatible, func(i, j int) bool {
		return compatible[i].Latency < compatible[j].Latency
	})

	sort.SliceStable(general, func(i, j int) bool {
		return general[i].Latency < general[j].Latency
	})

	return compatible, general
}

func (c *Checker) processNode(ctx context.Context, node *types.ProxyNode) {
	probeCtx, cancel := context.WithTimeout(ctx, c.options.Timeout)
	defer cancel()

	var success bool
	for _, dialer := range c.dialers {
		client, cleanup, err := dialer.NewHTTPClient(probeCtx, node)
		if err != nil {
			// If dialing fails, try the next dialer
			continue
		}

		// Map URLs to feature tags
		featureMap := map[string]string{
			"https://aistudio.google.com": "[AI-Studio]",
			"https://notebooklm.google.com": "[NotebookLM]",
			"https://jules.google.com": "[Jules]",
			"https://generativelanguage.googleapis.com/v1beta/models": "[API]",
		}

		var wg sync.WaitGroup
		var mu sync.Mutex

		allURLs := append([]string{}, c.options.CoreTargetURLs...)
		allURLs = append(allURLs, c.options.SecondaryTargetURLs...)

		results := make(map[string]ProbeResult)

		for _, url := range allURLs {
			wg.Add(1)
			go func(u string) {
				defer wg.Done()
				res := Probe(probeCtx, client, node, u)
				mu.Lock()
				results[u] = res
				mu.Unlock()
			}(url)
		}

		wg.Wait()

		if cleanup != nil {
			cleanup()
		}

		// Check if the node is alive (any probe was able to connect)
		isAlive := false
		var totalLatency time.Duration
		var successfulProbes int

		for _, res := range results {
			if res.IsAlive {
				isAlive = true
				totalLatency += res.Latency
				successfulProbes++
			}
		}

		if !isAlive {
			// If not alive, the proxy client failed to proxy traffic,
			// try the next dialer.
			continue
		}

		success = true
		node.IsAlive = true
		if successfulProbes > 0 {
			node.Latency = totalLatency / time.Duration(successfulProbes)
		}

		// Tier 1 qualification: Passes at least one Core Gemini Service
		passedCore := false
		for _, u := range c.options.CoreTargetURLs {
			if results[u].Compatible {
				passedCore = true
				break
			}
		}

		if passedCore {
			node.IsGeminiCompatible = true
			// Append feature tags
			var features []string
			for _, u := range allURLs {
				if results[u].Compatible {
					if tag, exists := featureMap[u]; exists {
						features = append(features, tag)
					}
				}
			}
			node.Features = features
		} else {
			node.IsGeminiCompatible = false
		}

		break
	}

	if !success {
		node.IsGeminiCompatible = false
		node.IsAlive = false
	}
}

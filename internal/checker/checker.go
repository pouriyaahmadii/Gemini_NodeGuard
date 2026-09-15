package checker

import (
	"context"
	"sort"
	"sync"
	"time"

	"gemini-sub-checker/internal/types"
)

// CheckOptions configures the behavior of the checker pool.
type CheckOptions struct {
	Concurrency int
	Timeout     time.Duration
	TargetURLs  []string
}

// DefaultCheckOptions returns recommended default settings.
func DefaultCheckOptions() CheckOptions {
	return CheckOptions{
		Concurrency: 10,
		Timeout:     8 * time.Second,
		TargetURLs: []string{
			"https://gemini.google.com",
		},
	}
}

// Checker orchestrates the concurrent testing of ProxyNodes.
type Checker struct {
	options CheckOptions
	dialer  NodeDialer
}

// NewChecker initializes a new Checker with given options and dialer.
func NewChecker(opts CheckOptions, dialer NodeDialer) *Checker {
	if opts.Concurrency <= 0 {
		opts.Concurrency = 10
	}
	if opts.Timeout <= 0 {
		opts.Timeout = 8 * time.Second
	}
	if len(opts.TargetURLs) == 0 {
		opts.TargetURLs = []string{"https://gemini.google.com"}
	}
	return &Checker{
		options: opts,
		dialer:  dialer,
	}
}

// CheckAll executes the checking process across all provided nodes,
// and returns a filtered and sorted slice of compatible nodes.
func (c *Checker) CheckAll(ctx context.Context, nodes []*types.ProxyNode) []*types.ProxyNode {
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
	for _, n := range nodes {
		if n.IsGeminiCompatible {
			compatible = append(compatible, n)
		}
	}

	sort.SliceStable(compatible, func(i, j int) bool {
		return compatible[i].Latency < compatible[j].Latency
	})

	return compatible
}

func (c *Checker) processNode(ctx context.Context, node *types.ProxyNode) {
	probeCtx, cancel := context.WithTimeout(ctx, c.options.Timeout)
	defer cancel()

	client, cleanup, err := c.dialer.NewHTTPClient(probeCtx, node)
	if err != nil {
		node.IsGeminiCompatible = false
		return
	}
	if cleanup != nil {
		defer cleanup()
	}

	// In the real case, maybe we probe multiple URLs until success.
	// We'll test against the first target URL for compatibility.
	// If it fails, maybe try others? Requirements don't strictly define fallback logic,
	// so we'll just test all target URLs or until one passes.
	for _, url := range c.options.TargetURLs {
		Probe(probeCtx, client, node, url)
		if node.IsGeminiCompatible {
			break
		}
	}
}

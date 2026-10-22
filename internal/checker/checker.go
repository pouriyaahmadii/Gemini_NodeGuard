package checker

import (
	"context"
	"sync"
	"time"

	"gemini-nodeguard/internal/registry"
	"gemini-nodeguard/internal/types"
)

// CheckOptions configures the behavior of the checker pool.
type CheckOptions struct {
	Concurrency int
	Timeout     time.Duration
	Services    []string
}

// DefaultCheckOptions returns recommended default settings.
func DefaultCheckOptions() CheckOptions {
	return CheckOptions{
		Concurrency: 10,
		Timeout:     8 * time.Second,
		Services:    []string{"search", "gemini", "aistudio", "notebooklm", "jules", "ai_api"},
	}
}

// Checker orchestrates the concurrent testing of ProxyNodes.
type Checker struct {
	options CheckOptions
	dialers []NodeDialer
	svcReg  map[string]*registry.ServiceDefinition
}

// NewChecker initializes a new Checker with given options and dialers.
func NewChecker(opts CheckOptions, dialers ...NodeDialer) *Checker {
	if opts.Concurrency <= 0 {
		opts.Concurrency = 10
	}
	if opts.Timeout <= 0 {
		opts.Timeout = 8 * time.Second
	}

	// Ensure at least "search" is present for general connectivity if empty
	if len(opts.Services) == 0 {
		opts.Services = []string{"search"}
	}

	return &Checker{
		options: opts,
		dialers: dialers,
		svcReg:  registry.GetServices(),
	}
}

// CheckAll executes the checking process across all provided nodes.
// It returns a slice containing the modified ProxyNodes with updated status and latency.
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

	return nodes
}

func (c *Checker) processNode(ctx context.Context, node *types.ProxyNode) {
	probeCtx, cancel := context.WithTimeout(ctx, c.options.Timeout)
	defer cancel()

	var success bool

	// Collect valid definitions from the registry based on options
	var activeServices []*registry.ServiceDefinition
	for _, svcID := range c.options.Services {
		if def, exists := c.svcReg[svcID]; exists {
			activeServices = append(activeServices, def)
		}
	}

	for _, dialer := range c.dialers {
		client, cleanup, err := dialer.NewHTTPClient(probeCtx, node)
		if err != nil {
			// If dialing fails, try the next dialer
			continue
		}

		var wg sync.WaitGroup
		var mu sync.Mutex

		results := make(map[string]types.ServiceResult)

		for _, svc := range activeServices {
			wg.Add(1)
			go func(def *registry.ServiceDefinition) {
				defer wg.Done()
				res := Probe(probeCtx, client, node, def)
				mu.Lock()
				results[def.ID] = res
				mu.Unlock()
			}(svc)
		}

		wg.Wait()

		if cleanup != nil {
			cleanup()
		}

		// Evaluate general aliveness based on transport errors across services
		isAlive := false
		var totalLatency time.Duration
		var validProbes int

		for svcID, res := range results {
			if res.Classification != types.ClassificationTransportError {
				isAlive = true
				totalLatency += res.Latency
				validProbes++
			}
			node.ServiceResults[svcID] = res
		}

		if !isAlive {
			// Proxy completely failed transport for all probes, try next dialer
			continue
		}

		success = true
		node.IsAlive = true
		if validProbes > 0 {
			node.Latency = totalLatency / time.Duration(validProbes)
		}

		break
	}

	if !success {
		node.IsAlive = false
	}
}

package checker

import (
	"context"
	"fmt"
	"sync"
	"time"

	"gemini-nodeguard/internal/registry"
	"gemini-nodeguard/internal/types"
)

// CheckOptions configures the behavior of the checker pool.
type CheckOptions struct {
	Concurrency         int
	ProcessStartTimeout time.Duration
	ProbeTimeout        time.Duration
	NodeTimeout         time.Duration
	ConfirmCount        int
	Services            []string
}

// DefaultCheckOptions returns recommended default settings.
func DefaultCheckOptions() CheckOptions {
	return CheckOptions{
		Concurrency:         10,
		ProcessStartTimeout: 5 * time.Second,
		ProbeTimeout:        8 * time.Second,
		NodeTimeout:         25 * time.Second,
		ConfirmCount:        1,
		Services:            []string{"search", "gemini", "aistudio", "notebooklm", "jules", "ai_api"},
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
	if opts.ProcessStartTimeout <= 0 {
		opts.ProcessStartTimeout = 5 * time.Second
	}
	if opts.ProbeTimeout <= 0 {
		opts.ProbeTimeout = 8 * time.Second
	}
	if opts.NodeTimeout <= 0 {
		opts.NodeTimeout = 25 * time.Second
	}
	if opts.ConfirmCount <= 0 {
		opts.ConfirmCount = 1
	} else if opts.ConfirmCount > 3 {
		opts.ConfirmCount = 3
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
	nodeCtx, cancelNode := context.WithTimeout(ctx, c.options.NodeTimeout)
	defer cancelNode()

	var success bool

	// Collect valid definitions from the registry based on options
	var activeServices []*registry.ServiceDefinition
	for _, svcID := range c.options.Services {
		if def, exists := c.svcReg[svcID]; exists {
			activeServices = append(activeServices, def)
		}
	}

	for _, dialer := range c.dialers {
		startCtx, cancelStart := context.WithTimeout(nodeCtx, c.options.ProcessStartTimeout)
		client, cleanup, err := dialer.NewHTTPClient(startCtx, node)
		cancelStart()

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

				var lastRes types.ServiceResult
				var totalLatency time.Duration
				successes := 0

				for i := 0; i < c.options.ConfirmCount; i++ {
					probeCtx, cancelProbe := context.WithTimeout(nodeCtx, c.options.ProbeTimeout)
					res := Probe(probeCtx, client, node, def)
					cancelProbe()

					if res.Classification == types.ClassificationConfirmedCompatible {
						successes++
						totalLatency += res.Latency
					} else {
						lastRes = res
						break // stop confirming if it failed
					}
				}

				mu.Lock()
				defer mu.Unlock()

				if successes == c.options.ConfirmCount {
					// All confirmations succeeded
					results[def.ID] = types.ServiceResult{
						Classification: types.ClassificationConfirmedCompatible,
						Latency:        totalLatency / time.Duration(successes),
						HTTPStatus:     200, // Best effort representation
					}
				} else if successes > 0 {
					// Flaky node
					results[def.ID] = types.ServiceResult{
						Classification: types.ClassificationInconclusive,
						Diagnostic:     fmt.Sprintf("unstable: %d/%d probes succeeded", successes, c.options.ConfirmCount),
						Latency:        totalLatency / time.Duration(successes),
					}
				} else {
					// First probe failed
					results[def.ID] = lastRes
				}
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

package checker

import (
	"context"
	"fmt"
	"net"
	"sync"
	"time"

	"gemini-nodeguard/internal/types"
)

// PrefilterResult holds the outcome of a TCP prefilter attempt.
type PrefilterResult struct {
	Alive      bool
	Diagnostic string
}

// TCPPrefilter performs a quick TCP connection to check if the node's server and port are reachable.
func TCPPrefilter(ctx context.Context, nodes []*types.ProxyNode, concurrency int) {
	if concurrency <= 0 {
		concurrency = 10
	}

	workQueue := make(chan *types.ProxyNode, len(nodes))
	var wg sync.WaitGroup

	for _, n := range nodes {
		workQueue <- n
	}
	close(workQueue)

	for i := 0; i < concurrency; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for node := range workQueue {
				// Stop processing if context is canceled
				select {
				case <-ctx.Done():
					return
				default:
				}

				if node.Server == "" || node.Port == 0 {
					continue
				}

				addr := fmt.Sprintf("%s:%d", node.Server, node.Port)
				d := net.Dialer{Timeout: 2500 * time.Millisecond}
				conn, err := d.DialContext(ctx, "tcp", addr)
				if err != nil {
					node.IsAlive = false
					// Set prefilter failure on all active services
					for svcID := range node.ServiceResults {
						diag := "tcp_prefilter_failed: "
						if _, ok := err.(*net.DNSError); ok {
							diag += "dns_resolution_failed"
						} else if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
							diag += "timeout"
						} else {
							diag += "connection_refused"
						}

						res := node.ServiceResults[svcID]
						res.Classification = types.ClassificationTransportError
						res.Diagnostic = diag
						node.ServiceResults[svcID] = res
					}
					// Mark node as completely dead early
				} else {
					conn.Close()
				}
			}
		}()
	}

	wg.Wait()
}

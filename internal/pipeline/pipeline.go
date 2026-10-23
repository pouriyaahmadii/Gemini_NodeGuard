package pipeline

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"time"

	"gemini-nodeguard/internal/checker"
	"gemini-nodeguard/internal/exporter"
	"gemini-nodeguard/internal/fetcher"
	"gemini-nodeguard/internal/parser"
	"gemini-nodeguard/internal/registry"
	"gemini-nodeguard/internal/types"
	"gemini-nodeguard/internal/utils"
)

type PipelineConfig struct {
	SubURLs     []string
	Services    []string
	OutputDir   string
	ReportPath  string
	Concurrency int
	Timeout     time.Duration
	Dialers     []checker.NodeDialer
	MaxNodes    int
	Seed        int64
	Prefilter   bool
}

type RunSummary struct {
	Version      string            `json:"version"`
	Timestamp    time.Time         `json:"timestamp"`
	TotalFetched int               `json:"total_fetched"`
	TotalParsed  int               `json:"total_parsed"`
	Prefilter    map[string]int    `json:"prefilter"`
	TotalAlive   int               `json:"total_alive"`
	Services     map[string]int    `json:"services"`
	Nodes        []NodeReportEntry `json:"nodes"`
}

type NodeReportEntry struct {
	URL            string                         `json:"url"`
	Protocol       string                         `json:"protocol"`
	PrefilterState string                         `json:"prefilter_state,omitempty"`
	Services       map[string]types.ServiceResult `json:"services"`
}

func RunPipeline(ctx context.Context, cfg PipelineConfig) (int, error) {
	log.Println("Fetching subscriptions...")

	fOpts := fetcher.FetchOptions{
		Timeout: cfg.Timeout,
	}
	f := fetcher.NewFetcher(fOpts)

	rawLines, err := f.FetchAll(ctx, cfg.SubURLs)
	if err != nil {
		log.Printf("Some fetches failed: %v", err)
	}
	if len(rawLines) == 0 {
		return 1, fmt.Errorf("no raw URIs fetched")
	}
	log.Printf("Fetched %d raw URIs.", len(rawLines))

	log.Println("Parsing URIs...")
	nodes, parseErrs := parser.ParseAll(rawLines)
	if len(parseErrs) > 0 {
		log.Printf("Encountered %d parse errors", len(parseErrs))
	}
	if len(nodes) == 0 {
		return 1, fmt.Errorf("no valid nodes parsed")
	}

	if cfg.MaxNodes > 0 && len(nodes) > cfg.MaxNodes {
		nodes = nodes[:cfg.MaxNodes]
		log.Printf("Sampled down to %d nodes", cfg.MaxNodes)
	}

	log.Printf("Parsed %d unique, valid nodes.", len(nodes))

	summary := RunSummary{
		Version:      "v1",
		Timestamp:    time.Now(),
		TotalFetched: len(rawLines),
		TotalParsed:  len(nodes),
		Prefilter:    make(map[string]int),
		Services:     make(map[string]int),
		Nodes:        []NodeReportEntry{},
	}

	if cfg.Prefilter {
		log.Println("Running TCP prefilter...")
		checker.TCPPrefilter(ctx, nodes, cfg.Concurrency)
	}

	log.Println("Checking node connectivity...")
	checkOpts := checker.CheckOptions{
		Concurrency:         cfg.Concurrency,
		ProcessStartTimeout: 5 * time.Second,
		ProbeTimeout:        8 * time.Second,
		NodeTimeout:         cfg.Timeout,
		ConfirmCount:        1,
		Services:            cfg.Services,
	}

	c := checker.NewChecker(checkOpts, cfg.Dialers...)
	processedNodes := c.CheckAll(ctx, nodes)

	var generalNodes []*types.ProxyNode
	serviceNodes := make(map[string][]*types.ProxyNode)
	for _, svc := range cfg.Services {
		serviceNodes[svc] = []*types.ProxyNode{}
	}

	for _, n := range processedNodes {
		entry := NodeReportEntry{
			URL:      utils.RedactURL(n.RawURI),
			Protocol: string(n.Protocol),
			Services: n.ServiceResults,
		}

		prefilterState := "passed"
		for _, res := range n.ServiceResults {
			if res.Classification == types.ClassificationTransportError {
				if len(res.Diagnostic) >= 19 && res.Diagnostic[:19] == "tcp_prefilter_failed" {
					prefilterState = "failed"
				}
			}
			break
		}

		entry.PrefilterState = prefilterState
		summary.Nodes = append(summary.Nodes, entry)

		if !n.IsAlive {
			continue
		}

		generalNodes = append(generalNodes, n)

		for svcID, res := range n.ServiceResults {
			if res.Classification == types.ClassificationConfirmedCompatible {
				serviceNodes[svcID] = append(serviceNodes[svcID], n)
			}
		}
	}

	summary.TotalAlive = len(generalNodes)
	for k, v := range serviceNodes {
		summary.Services[k] = len(v)
	}

	if err := os.MkdirAll(cfg.OutputDir, 0o700); err != nil {
		return 1, fmt.Errorf("failed to create output dir: %w", err)
	}

	svcReg := registry.GetServices()

	for svcID, sNodes := range serviceNodes {
		if len(sNodes) > 0 {
			def := svcReg[svcID]
			sort.SliceStable(sNodes, func(i, j int) bool {
				return sNodes[i].ServiceResults[svcID].Latency < sNodes[j].ServiceResults[svcID].Latency
			})
			opts := exporter.ExportOptions{
				RemarkPrefix:   fmt.Sprintf("[%s]", def.Name),
				IncludeLatency: true,
				ServiceID:      svcID,
			}
			plainText := exporter.ExportPlain(sNodes, opts)
			outPath := filepath.Join(cfg.OutputDir, fmt.Sprintf("%s-sub.txt", svcID))

			if err := exporter.WriteToFile(outPath, plainText); err != nil {
				log.Printf("Failed to write %s nodes to file: %v", def.Name, err)
			}
		}
	}

	if len(generalNodes) > 0 {
		sort.SliceStable(generalNodes, func(i, j int) bool {
			return generalNodes[i].Latency < generalNodes[j].Latency
		})

		opts := exporter.ExportOptions{
			RemarkPrefix:   "[General]",
			IncludeLatency: true,
		}
		plainText := exporter.ExportPlain(generalNodes, opts)
		generalPath := filepath.Join(cfg.OutputDir, "general-sub.txt")
		if err := exporter.WriteToFile(generalPath, plainText); err != nil {
			log.Printf("Failed to write general nodes to file: %v", err)
		}
	}

	if cfg.ReportPath != "" {
		f, err := os.Create(cfg.ReportPath)
		if err == nil {
			json.NewEncoder(f).Encode(summary)
			f.Close()
		}
	}

	fmt.Println("\n--- Summary ---")
	fmt.Printf("Total Fetched:  %d\n", summary.TotalFetched)
	fmt.Printf("Total Parsed:   %d\n", summary.TotalParsed)
	fmt.Printf("Total Alive:    %d\n", summary.TotalAlive)
	for _, svcID := range cfg.Services {
		def := svcReg[svcID]
		if def != nil {
			fmt.Printf("%-20s : %d\n", def.Name, summary.Services[svcID])
		}
	}

	if summary.TotalAlive == 0 {
		return 2, nil
	}

	return 0, nil
}

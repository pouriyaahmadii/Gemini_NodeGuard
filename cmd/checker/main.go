package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"sort"
	"strings"
	"syscall"
	"time"

	"gemini-nodeguard/internal/banner"
	"gemini-nodeguard/internal/checker"
	"gemini-nodeguard/internal/config"
	"gemini-nodeguard/internal/exporter"
	"gemini-nodeguard/internal/fetcher"
	"gemini-nodeguard/internal/parser"
	"gemini-nodeguard/internal/registry"
	"gemini-nodeguard/internal/types"
)

func main() {
	var (
		configPath  string
		subURLs     string
		servicesStr string
		outputDir   string
		concurrency int
		timeoutStr  string
		singboxPath string
		xrayPath    string
		silent      bool
	)

	flag.StringVar(&configPath, "config", "", "Path to config JSON file")
	flag.StringVar(&subURLs, "sub", "", "Comma-separated subscription URLs")
	flag.StringVar(&servicesStr, "services", "", "Comma-separated list of services to test (e.g. search,gemini,notebooklm)")
	flag.StringVar(&outputDir, "out-dir", "", "Directory path to save the output subscription files")
	flag.IntVar(&concurrency, "concurrency", 0, "Integer worker count")
	flag.StringVar(&timeoutStr, "timeout", "", "Timeout duration (e.g. 8s)")
	flag.StringVar(&singboxPath, "singbox", "", "Path to sing-box binary")
	flag.StringVar(&xrayPath, "xray", "", "Path to xray binary")
	flag.BoolVar(&silent, "silent", false, "Suppress banner and print only essential logs")
	flag.BoolVar(&silent, "quiet", false, "Suppress banner and print only essential logs (alias for -silent)")
	flag.Parse()

	// Print banner unless silent flag is true
	if !silent {
		banner.PrintBanner()
	}

	// Load configuration
	cfg, err := config.LoadConfig(configPath)
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// Override with CLI flags
	if subURLs != "" {
		cfg.SubURLs = append(cfg.SubURLs, strings.Split(subURLs, ",")...)
	}
	if servicesStr != "" {
		cfg.Services = strings.Split(servicesStr, ",")
	}
	if outputDir != "" {
		cfg.OutputDir = outputDir
	}
	if concurrency > 0 {
		cfg.Concurrency = concurrency
	}
	if timeoutStr != "" {
		cfg.TimeoutStr = timeoutStr
		parsedTimeout, err := time.ParseDuration(timeoutStr)
		if err != nil {
			log.Fatalf("Invalid timeout duration format: %v", err)
		}
		cfg.Timeout = parsedTimeout
	}
	if singboxPath != "" {
		cfg.SingboxPath = singboxPath
	}
	if xrayPath != "" {
		cfg.XrayPath = xrayPath
	}

	if len(cfg.SubURLs) == 0 {
		log.Fatal("No subscription URLs provided. Use -sub or specify in config.")
	}

	// Validate services against registry
	svcReg := registry.GetServices()
	var validServices []string
	for _, s := range cfg.Services {
		s = strings.TrimSpace(s)
		if _, exists := svcReg[s]; exists {
			validServices = append(validServices, s)
		} else {
			log.Printf("Warning: Unknown service %q specified, ignoring.", s)
		}
	}
	if len(validServices) == 0 {
		log.Fatal("No valid services specified to test.")
	}
	cfg.Services = validServices

	log.Printf("Starting Google Services NodeGuard")
	log.Printf("Testing Services: %v", cfg.Services)
	log.Printf("Concurrency: %d, Timeout: %s, Output Dir: %s", cfg.Concurrency, cfg.Timeout, cfg.OutputDir)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Handle graceful shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-sigCh
		log.Println("Received termination signal, shutting down...")
		cancel()
	}()

	// 1. Fetch Subscriptions
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
		log.Fatalf("No raw URIs fetched.")
	}
	log.Printf("Fetched %d raw URIs.", len(rawLines))

	// 2. Parse Nodes
	log.Println("Parsing URIs...")
	nodes, parseErrs := parser.ParseAll(rawLines)
	if len(parseErrs) > 0 {
		log.Printf("Encountered %d parse errors", len(parseErrs))
	}
	if len(nodes) == 0 {
		log.Fatalf("No valid nodes parsed.")
	}
	log.Printf("Parsed %d unique, valid nodes.", len(nodes))

	// 3. Check Nodes
	log.Println("Checking node connectivity...")
	checkOpts := checker.CheckOptions{
		Concurrency: cfg.Concurrency,
		Timeout:     cfg.Timeout,
		Services:    cfg.Services,
	}

	resolvedSingboxPath, err := checker.ResolveSingbox(cfg.SingboxPath)
	if err != nil {
		log.Printf("Warning: Failed to resolve sing-box binary: %v", err)
	}

	var dialers []checker.NodeDialer

	singboxDialer, err := checker.NewSingboxBinaryDialer(resolvedSingboxPath)
	if err != nil {
		log.Printf("Warning: Failed to initialize sing-box dialer: %v", err)
		log.Println("Node connectivity checks might fail if sing-box is not properly installed.")
	} else {
		dialers = append(dialers, singboxDialer)
	}

	resolvedXrayPath, err := checker.ResolveXray(cfg.XrayPath)
	if err != nil {
		log.Printf("Warning: Failed to resolve xray binary: %v", err)
	}

	xrayDialer, err := checker.NewXrayBinaryDialer(resolvedXrayPath)
	if err != nil {
		log.Printf("Warning: Failed to initialize xray dialer: %v", err)
	} else {
		dialers = append(dialers, xrayDialer)
	}

	c := checker.NewChecker(checkOpts, dialers...)

	processedNodes := c.CheckAll(ctx, nodes)

	// 4. Organize and Export

	// Separate general alive nodes from service-specific compatible nodes
	var generalNodes []*types.ProxyNode
	serviceNodes := make(map[string][]*types.ProxyNode)
	for _, svc := range cfg.Services {
		serviceNodes[svc] = []*types.ProxyNode{}
	}

	for _, n := range processedNodes {
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

	sort.SliceStable(generalNodes, func(i, j int) bool {
		return generalNodes[i].Latency < generalNodes[j].Latency
	})

	if len(generalNodes) == 0 {
		log.Println("No alive nodes found to export. Exiting.")
		return
	}

	// Create output directory
	if err := os.MkdirAll(cfg.OutputDir, 0700); err != nil {
		log.Fatalf("Failed to create output directory %q: %v", cfg.OutputDir, err)
	}

	// Export service-specific nodes
	for svcID, sNodes := range serviceNodes {
		if len(sNodes) > 0 {
			def := svcReg[svcID]
			sort.SliceStable(sNodes, func(i, j int) bool {
				return sNodes[i].ServiceResults[svcID].Latency < sNodes[j].ServiceResults[svcID].Latency
			})
			opts := exporter.ExportOptions{
				RemarkPrefix:   fmt.Sprintf("[%s]", def.Name),
				IncludeLatency: true,
			}
			plainText := exporter.ExportPlain(sNodes, opts)
			outPath := filepath.Join(cfg.OutputDir, fmt.Sprintf("%s-sub.txt", svcID))

			if err := exporter.WriteToFile(outPath, plainText); err != nil {
				log.Printf("Failed to write %s nodes to file: %v", def.Name, err)
			} else {
				log.Printf("Exported %d %s nodes to %s", len(sNodes), def.Name, outPath)
			}
		}
	}

	// Export General
	expOptsGeneral := exporter.ExportOptions{
		RemarkPrefix:   "[General]",
		IncludeLatency: true,
	}

	plainText := exporter.ExportPlain(generalNodes, expOptsGeneral)
	generalPath := filepath.Join(cfg.OutputDir, "general-sub.txt")
	if err := exporter.WriteToFile(generalPath, plainText); err != nil {
		log.Fatalf("Failed to write General nodes to file: %v", err)
	}
	log.Printf("Exported %d General nodes to %s", len(generalNodes), generalPath)

	log.Println("Export successful!")

	// Print Summary
	fmt.Println("\n--- Summary ---")
	fmt.Printf("Total Fetched:  %d\n", len(rawLines))
	fmt.Printf("Total Parsed:   %d\n", len(nodes))
	fmt.Printf("Total Alive:    %d\n", len(generalNodes))
	fmt.Println("--- Service Compatibility ---")
	for _, svcID := range cfg.Services {
		def := svcReg[svcID]
		fmt.Printf("%-20s : %d\n", def.Name, len(serviceNodes[svcID]))
	}
}

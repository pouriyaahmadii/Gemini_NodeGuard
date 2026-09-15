package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"gemini-sub-checker/internal/checker"
	"gemini-sub-checker/internal/config"
	"gemini-sub-checker/internal/exporter"
	"gemini-sub-checker/internal/fetcher"
	"gemini-sub-checker/internal/parser"
)

func main() {
	var (
		configPath  string
		subURLs     string
		outputPath  string
		concurrency int
		timeoutStr  string
		singboxPath string
	)

	flag.StringVar(&configPath, "config", "", "Path to config JSON file")
	flag.StringVar(&subURLs, "sub", "", "Comma-separated subscription URLs")
	flag.StringVar(&outputPath, "out", "", "Output file path")
	flag.IntVar(&concurrency, "concurrency", 0, "Integer worker count")
	flag.StringVar(&timeoutStr, "timeout", "", "Timeout duration (e.g. 8s)")
	flag.StringVar(&singboxPath, "singbox", "", "Path to sing-box binary")
	flag.Parse()

	// Load configuration
	cfg, err := config.LoadConfig(configPath)
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// Override with CLI flags
	if subURLs != "" {
		cfg.SubURLs = append(cfg.SubURLs, strings.Split(subURLs, ",")...)
	}
	if outputPath != "" {
		cfg.OutputPath = outputPath
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

	if len(cfg.SubURLs) == 0 {
		log.Fatal("No subscription URLs provided. Use -sub or specify in config.")
	}

	log.Printf("Starting Gemini Sub Checker")
	log.Printf("Concurrency: %d, Timeout: %s, Output: %s", cfg.Concurrency, cfg.Timeout, cfg.OutputPath)

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
	}

	dialer, err := checker.NewSingboxBinaryDialer(cfg.SingboxPath)
	if err != nil {
		log.Printf("Warning: Failed to initialize sing-box dialer: %v", err)
		log.Println("Node connectivity checks might fail if sing-box is not properly installed.")
	}
	c := checker.NewChecker(checkOpts, dialer)

	validNodes := c.CheckAll(ctx, nodes)

	log.Printf("Checking completed. Found %d compatible nodes.", len(validNodes))

	// 4. Export
	if len(validNodes) == 0 {
		log.Println("No compatible nodes found to export. Exiting.")
		return
	}

	log.Printf("Exporting nodes to %s...", cfg.OutputPath)
	expOpts := exporter.ExportOptions{
		RemarkPrefix:   cfg.RemarkPrefix,
		IncludeLatency: true,
	}

	encoded, err := exporter.ExportBase64(validNodes, expOpts)
	if err != nil {
		log.Fatalf("Failed to export to base64: %v", err)
	}

	if err := exporter.WriteToFile(cfg.OutputPath, encoded); err != nil {
		log.Fatalf("Failed to write to file: %v", err)
	}

	log.Println("Export successful!")

	// Print Summary
	fmt.Println("--- Summary ---")
	fmt.Printf("Total Fetched: %d\n", len(rawLines))
	fmt.Printf("Total Parsed:  %d\n", len(nodes))
	fmt.Printf("Compatible:    %d\n", len(validNodes))
}

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

	"gemini-nodeguard/internal/banner"
	"gemini-nodeguard/internal/checker"
	"gemini-nodeguard/internal/config"
	"gemini-nodeguard/internal/exporter"
	"gemini-nodeguard/internal/fetcher"
	"gemini-nodeguard/internal/parser"
)

func main() {
	var (
		configPath  string
		subURLs     string
		targetURLs  string
		outputPath  string
		concurrency int
		timeoutStr  string
		singboxPath string
		xrayPath    string
		silent      bool
	)

	flag.StringVar(&configPath, "config", "", "Path to config JSON file")
	flag.StringVar(&subURLs, "sub", "", "Comma-separated subscription URLs")
	flag.StringVar(&targetURLs, "targets", "", "Comma-separated target URLs to test")
	flag.StringVar(&outputPath, "out", "", "Output file path")
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
	if targetURLs != "" {
		cfg.TargetURLs = strings.Split(targetURLs, ",")
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
	if xrayPath != "" {
		cfg.XrayPath = xrayPath
	}

	if len(cfg.SubURLs) == 0 {
		log.Fatal("No subscription URLs provided. Use -sub or specify in config.")
	}

	log.Printf("Starting Gemini NodeGuard")
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
		TargetURLs:  cfg.TargetURLs,
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

	validNodes, generalNodes := c.CheckAll(ctx, nodes)

	log.Printf("Checking completed. Found %d Gemini compatible nodes and %d General nodes.", len(validNodes), len(generalNodes))

	// 4. Export
	if len(validNodes) == 0 && len(generalNodes) == 0 {
		log.Println("No compatible or general nodes found to export. Exiting.")
		return
	}

	expOptsGemini := exporter.ExportOptions{
		RemarkPrefix:   "[Gemini]",
		IncludeLatency: true,
	}
	expOptsGeneral := exporter.ExportOptions{
		RemarkPrefix:   "[General]",
		IncludeLatency: true,
	}

	// Export Gemini
	if len(validNodes) > 0 {
		log.Printf("Exporting Gemini nodes...")
		encoded, err := exporter.ExportBase64(validNodes, expOptsGemini)
		if err != nil {
			log.Fatalf("Failed to export Gemini nodes to base64: %v", err)
		}

		geminiPath := "output/gemini-sub.txt"
		if cfg.OutputPath != "" {
			geminiPath = cfg.OutputPath
		}
		if err := exporter.WriteToFile(geminiPath, encoded); err != nil {
			log.Fatalf("Failed to write Gemini nodes to file: %v", err)
		}
		log.Printf("Exported Gemini nodes to %s", geminiPath)
	}

	// Export General
	if len(generalNodes) > 0 {
		log.Printf("Exporting General nodes...")
		encoded, err := exporter.ExportBase64(generalNodes, expOptsGeneral)
		if err != nil {
			log.Fatalf("Failed to export General nodes to base64: %v", err)
		}

		generalPath := "output/general-sub.txt"
		if err := exporter.WriteToFile(generalPath, encoded); err != nil {
			log.Fatalf("Failed to write General nodes to file: %v", err)
		}
		log.Printf("Exported General nodes to %s", generalPath)
	}

	log.Println("Export successful!")

	// Print Summary
	fmt.Println("--- Summary ---")
	fmt.Printf("Total Fetched: %d\n", len(rawLines))
	fmt.Printf("Total Parsed:  %d\n", len(nodes))
	fmt.Printf("Compatible (Gemini): %d\n", len(validNodes))
	fmt.Printf("General (Alive):     %d\n", len(generalNodes))
}

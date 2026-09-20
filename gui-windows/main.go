package main

import (
	"context"
	"fmt"
	"strings"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/app"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/dialog"
	"fyne.io/fyne/v2/widget"

	"gemini-nodeguard/internal/checker"
	"gemini-nodeguard/internal/config"
	"gemini-nodeguard/internal/exporter"
	"gemini-nodeguard/internal/fetcher"
	"gemini-nodeguard/internal/parser"
)

func main() {
	a := app.New()
	w := a.NewWindow("Gemini NodeGuard (Windows)")

	w.Resize(fyne.NewSize(600, 500))

	// Input fields
	subURLsEntry := widget.NewMultiLineEntry()
	subURLsEntry.SetPlaceHolder("Enter Subscription URLs (one per line)")

	outputPathEntry := widget.NewEntry()
	outputPathEntry.SetText("output")
	outputPathEntry.SetPlaceHolder("Output directory (e.g., output)")

	concurrencyEntry := widget.NewEntry()
	concurrencyEntry.SetText("10")

	// Status log
	statusLog := widget.NewMultiLineEntry()
	statusLog.Disable()
	statusLog.SetText("Ready.\n")

	logMsg := func(msg string) {
		statusLog.SetText(statusLog.Text + msg + "\n")
		statusLog.CursorColumn = 0
		statusLog.CursorRow = len(strings.Split(statusLog.Text, "\n"))
	}

	startButton := widget.NewButton("Start Checking", func() {
		subURLs := strings.Split(subURLsEntry.Text, "\n")
		var validSubs []string
		for _, url := range subURLs {
			url = strings.TrimSpace(url)
			if url != "" {
				validSubs = append(validSubs, url)
			}
		}

		if len(validSubs) == 0 {
			dialog.ShowError(fmt.Errorf("please enter at least one subscription URL"), w)
			return
		}

		logMsg("Starting Gemini NodeGuard...")

		// Run in background so GUI doesn't freeze
		go func() {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
			defer cancel()

			cfg := &config.Config{
				SubURLs:     validSubs,
				Concurrency: 10,
				Timeout:     10 * time.Second,
				OutputPath:  outputPathEntry.Text,
				TargetURLs:  []string{"https://gemini.google.com/"},
			}

			// 1. Fetch
			logMsg("Fetching subscriptions...")
			fOpts := fetcher.FetchOptions{Timeout: cfg.Timeout}
			f := fetcher.NewFetcher(fOpts)

			rawLines, err := f.FetchAll(ctx, cfg.SubURLs)
			if err != nil {
				logMsg(fmt.Sprintf("Some fetches failed: %v", err))
			}
			if len(rawLines) == 0 {
				logMsg("No raw URIs fetched. Aborting.")
				return
			}
			logMsg(fmt.Sprintf("Fetched %d raw URIs.", len(rawLines)))

			// 2. Parse
			logMsg("Parsing URIs...")
			nodes, parseErrs := parser.ParseAll(rawLines)
			if len(parseErrs) > 0 {
				logMsg(fmt.Sprintf("Encountered %d parse errors", len(parseErrs)))
			}
			if len(nodes) == 0 {
				logMsg("No valid nodes parsed. Aborting.")
				return
			}
			logMsg(fmt.Sprintf("Parsed %d valid nodes.", len(nodes)))

			// 3. Check
			logMsg("Checking node connectivity...")
			checkOpts := checker.CheckOptions{
				Concurrency: cfg.Concurrency,
				Timeout:     cfg.Timeout,
				TargetURLs:  cfg.TargetURLs,
			}

			var dialers []checker.NodeDialer
			singboxPath, _ := checker.ResolveSingbox("")
			if sb, err := checker.NewSingboxBinaryDialer(singboxPath); err == nil {
				dialers = append(dialers, sb)
			}
			xrayPath, _ := checker.ResolveXray("")
			if xr, err := checker.NewXrayBinaryDialer(xrayPath); err == nil {
				dialers = append(dialers, xr)
			}

			c := checker.NewChecker(checkOpts, dialers...)
			validNodes, generalNodes := c.CheckAll(ctx, nodes)

			logMsg(fmt.Sprintf("Found %d Gemini compatible and %d General nodes.", len(validNodes), len(generalNodes)))

			// 4. Export
			if len(validNodes) == 0 && len(generalNodes) == 0 {
				logMsg("No nodes to export.")
				return
			}

			logMsg("Exporting results...")
			// Create output dir if needed
			// Note: simplistic path handling for demo
			geminiPath := cfg.OutputPath + "/gemini-sub.txt"
			if len(validNodes) > 0 {
				plain := exporter.ExportPlain(validNodes, exporter.ExportOptions{RemarkPrefix: "[Gemini]", IncludeLatency: true})
				_ = exporter.WriteToFile(geminiPath, plain)
			}

			generalPath := cfg.OutputPath + "/general-sub.txt"
			if len(generalNodes) > 0 {
				plain := exporter.ExportPlain(generalNodes, exporter.ExportOptions{RemarkPrefix: "[General]", IncludeLatency: true})
				_ = exporter.WriteToFile(generalPath, plain)
			}

			logMsg(fmt.Sprintf("Export successful! Saved to %s", cfg.OutputPath))
		}()
	})

	content := container.NewBorder(
		container.NewVBox(
			widget.NewLabel("Gemini NodeGuard Configuration"),
			subURLsEntry,
			container.NewGridWithColumns(2,
				widget.NewLabel("Output Directory:"), outputPathEntry,
			),
			startButton,
		),
		nil, nil, nil,
		statusLog,
	)

	w.SetContent(content)
	w.ShowAndRun()
}

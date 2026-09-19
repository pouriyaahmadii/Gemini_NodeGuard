package checker

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"time"

	"gemini-sub-checker/internal/types"
)

// NodeDialer interface allows HTTP traffic to be routed through a proxy node.
type NodeDialer interface {
	NewHTTPClient(ctx context.Context, node *types.ProxyNode) (client *http.Client, cleanup func(), err error)
}

// SingboxBinaryDialer uses the local sing-box binary to dial through nodes.
type SingboxBinaryDialer struct {
	binaryPath string
}

// NewSingboxBinaryDialer creates a new SingboxBinaryDialer.
func NewSingboxBinaryDialer(binaryPath string) (*SingboxBinaryDialer, error) {
	if binaryPath == "" {
		binaryPath = "sing-box"
	}
	path, err := exec.LookPath(binaryPath)
	if err != nil {
		return nil, fmt.Errorf("sing-box binary '%s' not found: %w", binaryPath, err)
	}
	return &SingboxBinaryDialer{binaryPath: path}, nil
}

// NewHTTPClient creates a new HTTP client routed through a sing-box instance.
func (d *SingboxBinaryDialer) NewHTTPClient(ctx context.Context, node *types.ProxyNode) (*http.Client, func(), error) {
	outbound, err := BuildSingboxOutbound(node)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to build sing-box outbound: %w", err)
	}

	// 1. Get a free local port
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, nil, fmt.Errorf("failed to allocate local port: %w", err)
	}
	port := listener.Addr().(*net.TCPAddr).Port
	listener.Close() // Close immediately, sing-box will bind to it

	// 2. Write minimal sing-box config
	config := map[string]any{
		"log": map[string]any{
			"level": "panic", // Keep it quiet
		},
		"inbounds": []map[string]any{
			{
				"type":        "socks",
				"tag":         "socks-in",
				"listen":      "127.0.0.1",
				"listen_port": port,
			},
		},
		"outbounds": []map[string]any{
			outbound,
		},
	}

	configFile, err := os.CreateTemp("", "sing-box-*.json")
	if err != nil {
		return nil, nil, fmt.Errorf("failed to create temp config file: %w", err)
	}
	configFilePath := configFile.Name()

	if err := json.NewEncoder(configFile).Encode(config); err != nil {
		configFile.Close()
		os.Remove(configFilePath)
		return nil, nil, fmt.Errorf("failed to write sing-box config: %w", err)
	}
	configFile.Close()

	// 3. Start sing-box process
	cmd := exec.CommandContext(ctx, d.binaryPath, "run", "-c", configFilePath)

	if err := cmd.Start(); err != nil {
		os.Remove(configFilePath)
		return nil, nil, fmt.Errorf("failed to start sing-box process: %w", err)
	}

	// Prevent zombie process leak
	go func() {
		_ = cmd.Wait()
	}()

	cleanup := func() {
		if cmd.Process != nil {
			cmd.Process.Kill()
		}
		os.Remove(configFilePath)
	}

	// 4. Readiness check loop
	ready := false
	socksAddr := fmt.Sprintf("127.0.0.1:%d", port)

	// Polling loop to wait until sing-box binds the SOCKS port
	timeout := time.After(2 * time.Second)
	ticker := time.NewTicker(50 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			cleanup()
			return nil, nil, ctx.Err()
		case <-timeout:
			cleanup()
			return nil, nil, fmt.Errorf("sing-box process readiness timeout")
		case <-ticker.C:
			conn, err := net.DialTimeout("tcp", socksAddr, 50*time.Millisecond)
			if err == nil {
				conn.Close()
				ready = true
			}
		}
		if ready {
			break
		}
	}

	// 5. Create HTTP Client using SOCKS5 proxy
	proxyURL, err := url.Parse(fmt.Sprintf("socks5://%s", socksAddr))
	if err != nil {
		cleanup()
		return nil, nil, fmt.Errorf("failed to parse proxy URL: %w", err)
	}

	client := &http.Client{
		Transport: &http.Transport{
			Proxy:                 http.ProxyURL(proxyURL),
			TLSHandshakeTimeout:   5 * time.Second,
			ResponseHeaderTimeout: 6 * time.Second,
		},
	}

	return client, cleanup, nil
}

// XrayBinaryDialer uses the local xray binary to dial through nodes.
type XrayBinaryDialer struct {
	binaryPath string
}

// NewXrayBinaryDialer creates a new XrayBinaryDialer.
func NewXrayBinaryDialer(binaryPath string) (*XrayBinaryDialer, error) {
	if binaryPath == "" {
		binaryPath = "xray"
	}
	path, err := exec.LookPath(binaryPath)
	if err != nil {
		return nil, fmt.Errorf("xray binary '%s' not found: %w", binaryPath, err)
	}
	return &XrayBinaryDialer{binaryPath: path}, nil
}

// NewHTTPClient creates a new HTTP client routed through an xray instance.
func (d *XrayBinaryDialer) NewHTTPClient(ctx context.Context, node *types.ProxyNode) (*http.Client, func(), error) {
	outbound, err := BuildXrayOutbound(node)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to build xray outbound: %w", err)
	}

	// 1. Get a free local port
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, nil, fmt.Errorf("failed to allocate local port: %w", err)
	}
	port := listener.Addr().(*net.TCPAddr).Port
	listener.Close() // Close immediately, xray will bind to it

	// 2. Write minimal xray config
	config := map[string]any{
		"log": map[string]any{
			"loglevel": "none",
		},
		"inbounds": []map[string]any{
			{
				"port":     port,
				"listen":   "127.0.0.1",
				"protocol": "socks",
				"settings": map[string]any{
					"auth": "noauth",
					"udp":  false,
				},
			},
		},
		"outbounds": []map[string]any{
			outbound,
		},
	}

	configFile, err := os.CreateTemp("", "xray-*.json")
	if err != nil {
		return nil, nil, fmt.Errorf("failed to create temp config file: %w", err)
	}
	configFilePath := configFile.Name()

	if err := json.NewEncoder(configFile).Encode(config); err != nil {
		configFile.Close()
		os.Remove(configFilePath)
		return nil, nil, fmt.Errorf("failed to write xray config: %w", err)
	}
	configFile.Close()

	// 3. Start xray process
	cmd := exec.CommandContext(ctx, d.binaryPath, "run", "-c", configFilePath)

	if err := cmd.Start(); err != nil {
		os.Remove(configFilePath)
		return nil, nil, fmt.Errorf("failed to start xray process: %w", err)
	}

	// Prevent zombie process leak
	go func() {
		_ = cmd.Wait()
	}()

	cleanup := func() {
		if cmd.Process != nil {
			cmd.Process.Kill()
		}
		os.Remove(configFilePath)
	}

	// 4. Readiness check loop
	ready := false
	socksAddr := fmt.Sprintf("127.0.0.1:%d", port)

	// Polling loop to wait until xray binds the SOCKS port
	timeout := time.After(2 * time.Second)
	ticker := time.NewTicker(50 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			cleanup()
			return nil, nil, ctx.Err()
		case <-timeout:
			cleanup()
			return nil, nil, fmt.Errorf("xray process readiness timeout")
		case <-ticker.C:
			conn, err := net.DialTimeout("tcp", socksAddr, 50*time.Millisecond)
			if err == nil {
				conn.Close()
				ready = true
			}
		}
		if ready {
			break
		}
	}

	// 5. Create HTTP Client using SOCKS5 proxy
	proxyURL, err := url.Parse(fmt.Sprintf("socks5://%s", socksAddr))
	if err != nil {
		cleanup()
		return nil, nil, fmt.Errorf("failed to parse proxy URL: %w", err)
	}

	client := &http.Client{
		Transport: &http.Transport{
			Proxy:                 http.ProxyURL(proxyURL),
			TLSHandshakeTimeout:   5 * time.Second,
			ResponseHeaderTimeout: 6 * time.Second,
		},
	}

	return client, cleanup, nil
}

// MockDialer is for unit testing.
type MockDialer struct {
	Client *http.Client
}

// NewHTTPClient returns the mocked client.
func (d *MockDialer) NewHTTPClient(ctx context.Context, node *types.ProxyNode) (*http.Client, func(), error) {
	if d.Client == nil {
		return http.DefaultClient, func() {}, nil
	}
	return d.Client, func() {}, nil
}

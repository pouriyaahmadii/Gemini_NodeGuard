package checker

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"time"

	"gemini-nodeguard/internal/types"
)

// NodeDialer interface allows HTTP traffic to be routed through a proxy node.
type NodeDialer interface {
	NewHTTPClient(ctx context.Context, node *types.ProxyNode) (client *http.Client, cleanup func(), err error)
}

// ... Singbox Binary Dialer ...

type SingboxBinaryDialer struct {
	binaryPath string
}

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

func (d *SingboxBinaryDialer) NewHTTPClient(ctx context.Context, node *types.ProxyNode) (*http.Client, func(), error) {
	outbound, err := BuildSingboxOutbound(node)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to build sing-box outbound: %w", err)
	}

	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, nil, fmt.Errorf("failed to allocate local port: %w", err)
	}
	port := listener.Addr().(*net.TCPAddr).Port
	listener.Close()

	config := map[string]any{
		"log": map[string]any{
			"level": "error",
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

	cmd := exec.CommandContext(ctx, d.binaryPath, "run", "-c", configFilePath)

	var stderrBuf bytes.Buffer
	cmd.Stderr = &stderrBuf

	if err := cmd.Start(); err != nil {
		os.Remove(configFilePath)
		return nil, nil, fmt.Errorf("failed to start sing-box process: %w", err)
	}

	cmdDone := make(chan error, 1)
	go func() {
		cmdDone <- cmd.Wait()
	}()

	cleanup := func() {
		if cmd.Process != nil {
			cmd.Process.Kill()
		}
		os.Remove(configFilePath)
	}

	socksAddr := fmt.Sprintf("127.0.0.1:%d", port)
	ticker := time.NewTicker(50 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			cleanup()
			return nil, nil, ctx.Err()
		case err := <-cmdDone:
			cleanup()
			// Extract last 1KB of stderr for error message
			errStr := stderrBuf.String()
			if len(errStr) > 1024 {
				errStr = errStr[len(errStr)-1024:]
			}
			return nil, nil, fmt.Errorf("sing-box process exited unexpectedly: %v (stderr: %s)", err, errStr)
		case <-ticker.C:
			conn, err := net.DialTimeout("tcp", socksAddr, 50*time.Millisecond)
			if err == nil {
				conn.Close()
				goto Ready
			}
		}
	}

Ready:
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
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse // Important: Probe will handle redirects
		},
	}

	return client, cleanup, nil
}

// ... Xray Binary Dialer ...

type XrayBinaryDialer struct {
	binaryPath string
}

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

func (d *XrayBinaryDialer) NewHTTPClient(ctx context.Context, node *types.ProxyNode) (*http.Client, func(), error) {
	outbound, err := BuildXrayOutbound(node)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to build xray outbound: %w", err)
	}

	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, nil, fmt.Errorf("failed to allocate local port: %w", err)
	}
	port := listener.Addr().(*net.TCPAddr).Port
	listener.Close()

	config := map[string]any{
		"log": map[string]any{
			"loglevel": "error",
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

	cmd := exec.CommandContext(ctx, d.binaryPath, "run", "-c", configFilePath)

	var stderrBuf bytes.Buffer
	cmd.Stderr = &stderrBuf

	if err := cmd.Start(); err != nil {
		os.Remove(configFilePath)
		return nil, nil, fmt.Errorf("failed to start xray process: %w", err)
	}

	cmdDone := make(chan error, 1)
	go func() {
		cmdDone <- cmd.Wait()
	}()

	cleanup := func() {
		if cmd.Process != nil {
			cmd.Process.Kill()
		}
		os.Remove(configFilePath)
	}

	socksAddr := fmt.Sprintf("127.0.0.1:%d", port)
	ticker := time.NewTicker(50 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			cleanup()
			return nil, nil, ctx.Err()
		case err := <-cmdDone:
			cleanup()
			errStr := stderrBuf.String()
			if len(errStr) > 1024 {
				errStr = errStr[len(errStr)-1024:]
			}
			return nil, nil, fmt.Errorf("xray process exited unexpectedly: %v (stderr: %s)", err, errStr)
		case <-ticker.C:
			conn, err := net.DialTimeout("tcp", socksAddr, 50*time.Millisecond)
			if err == nil {
				conn.Close()
				goto Ready
			}
		}
	}

Ready:
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
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}

	return client, cleanup, nil
}

// MockDialer is for unit testing.
type MockDialer struct {
	Client *http.Client
}

func (d *MockDialer) NewHTTPClient(ctx context.Context, node *types.ProxyNode) (*http.Client, func(), error) {
	if d.Client == nil {
		client := &http.Client{
			CheckRedirect: func(req *http.Request, via []*http.Request) error {
				return http.ErrUseLastResponse
			},
		}
		return client, func() {}, nil
	}
	if d.Client.CheckRedirect == nil {
		d.Client.CheckRedirect = func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		}
	}
	return d.Client, func() {}, nil
}

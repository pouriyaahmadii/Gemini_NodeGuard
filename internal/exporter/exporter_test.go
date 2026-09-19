package exporter

import (
	"strings"
	"testing"
	"time"

	"gemini-nodeguard/internal/parser"
	"gemini-nodeguard/internal/types"
)

func TestExportVMess(t *testing.T) {
	node := &types.ProxyNode{
		Protocol: types.ProtocolVmess,
		Server:   "example.com",
		Port:     443,
		UUID:     "b831381d-6324-4d53-ad4f-8cda48b30811",
		AlterID:  0,
		Cipher:   "auto",
		Remarks:  "Test VMess",
		Transport: types.TransportConfig{
			Type: types.TransportWS,
			Path: "/chat",
			Host: "ws.example.com",
		},
		Security: types.SecurityTLS,
		TLS: types.TLSConfig{
			Enabled:    true,
			ServerName: "sni.example.com",
		},
		Latency: 150 * time.Millisecond,
	}

	opts := ExportOptions{
		RemarkPrefix:   "[Gemini]",
		IncludeLatency: true,
	}

	uri, err := ReconstructURI(node, opts)
	if err != nil {
		t.Fatalf("ReconstructURI failed: %v", err)
	}

	if !strings.HasPrefix(uri, "vmess://") {
		t.Errorf("Expected vmess:// prefix, got: %s", uri)
	}

	// Parse it back to verify
	parsedNode, err := parser.ParseURI(uri)
	if err != nil {
		t.Fatalf("ParseURI failed on generated URI: %v", err)
	}

	if parsedNode.Server != node.Server {
		t.Errorf("Expected server %s, got %s", node.Server, parsedNode.Server)
	}
	if parsedNode.Remarks != "[Gemini][150ms] Test VMess" {
		t.Errorf("Expected remarks '[Gemini][150ms] Test VMess', got '%s'", parsedNode.Remarks)
	}
	if parsedNode.Transport.Type != node.Transport.Type {
		t.Errorf("Expected transport type %s, got %s", node.Transport.Type, parsedNode.Transport.Type)
	}
}

func TestExportVLESS(t *testing.T) {
	node := &types.ProxyNode{
		Protocol: types.ProtocolVless,
		Server:   "example.com",
		Port:     443,
		UUID:     "b831381d-6324-4d53-ad4f-8cda48b30811",
		Remarks:  "Test VLESS + Symbols",
		Transport: types.TransportConfig{
			Type: types.TransportTCP,
		},
		Security: types.SecurityReality,
		Reality: types.RealityConfig{
			Enabled:   true,
			PublicKey: "public_key_here",
			ShortID:   "short_id",
		},
		TLS: types.TLSConfig{
			Enabled:     true,
			ServerName:  "sni.example.com",
			ALPN:        []string{"h2", "http/1.1"},
			Fingerprint: "chrome",
		},
		Flow:    "xtls-rprx-vision",
		Latency: 0,
	}

	opts := ExportOptions{
		RemarkPrefix:   "[Gemini]",
		IncludeLatency: true,
	}

	uri, err := ReconstructURI(node, opts)
	if err != nil {
		t.Fatalf("ReconstructURI failed: %v", err)
	}

	if !strings.HasPrefix(uri, "vless://") {
		t.Errorf("Expected vless:// prefix, got: %s", uri)
	}

	// Ensure fragment handles + symbols as spaces? Wait, url.QueryUnescape handles + as spaces.
	// But in standard URI parsing, if we use u.Fragment without query escaping the fragment itself, it might be correct or not depending on parser.
	// Let's check parser.ParseURI behaviour.
	parsedNode, err := parser.ParseURI(uri)
	if err != nil {
		t.Fatalf("ParseURI failed on generated URI: %v", err)
	}

	if parsedNode.Server != node.Server {
		t.Errorf("Expected server %s, got %s", node.Server, parsedNode.Server)
	}
	// Our ReconstructStandard just assigns u.Fragment = remark. url.URL.String() might escape it.
	// Our parser uses url.QueryUnescape(u.Fragment). If URL string unescapes to "+ Symbols", let's check it.
	if parsedNode.Remarks != "[Gemini] Test VLESS + Symbols" {
		t.Errorf("Expected remarks '[Gemini] Test VLESS + Symbols', got '%s'", parsedNode.Remarks)
	}
	if parsedNode.Flow != node.Flow {
		t.Errorf("Expected flow %s, got %s", node.Flow, parsedNode.Flow)
	}
	if parsedNode.Security != types.SecurityReality {
		t.Errorf("Expected security %s, got %s", types.SecurityReality, parsedNode.Security)
	}
}

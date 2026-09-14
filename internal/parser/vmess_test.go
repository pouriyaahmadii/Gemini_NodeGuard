package parser

import (
	"encoding/base64"
	"testing"

	"gemini-sub-checker/internal/types"
)

func TestParseVMess(t *testing.T) {
	// Helper to generate base64 string
	makeB64 := func(j string, urlSafe, noPadding bool) string {
		var encoded string
		if urlSafe {
			encoded = base64.URLEncoding.EncodeToString([]byte(j))
		} else {
			encoded = base64.StdEncoding.EncodeToString([]byte(j))
		}
		if noPadding {
			for encoded[len(encoded)-1] == '=' {
				encoded = encoded[:len(encoded)-1]
			}
		}
		return "vmess://" + encoded
	}

	tests := []struct {
		name    string
		uri     string
		wantErr bool
		check   func(*testing.T, *types.ProxyNode)
	}{
		{
			name: "Standard VMess JSON with numeric port and aid",
			uri: makeB64(`{
				"v": "2",
				"ps": "Standard Node",
				"add": "example.com",
				"port": 443,
				"id": "test-uuid",
				"aid": 0,
				"scy": "auto",
				"net": "ws",
				"type": "none",
				"host": "ws.example.com",
				"path": "/ray",
				"tls": "tls",
				"sni": "sni.example.com",
				"alpn": "h2",
				"fp": "chrome"
			}`, false, false),
			wantErr: false,
			check: func(t *testing.T, node *types.ProxyNode) {
				if node.Protocol != types.ProtocolVmess {
					t.Errorf("Expected VMess protocol, got %v", node.Protocol)
				}
				if node.Server != "example.com" {
					t.Errorf("Expected server example.com, got %v", node.Server)
				}
				if node.Port != 443 {
					t.Errorf("Expected port 443, got %v", node.Port)
				}
				if node.UUID != "test-uuid" {
					t.Errorf("Expected UUID test-uuid, got %v", node.UUID)
				}
				if node.Remarks != "Standard Node" {
					t.Errorf("Expected remarks Standard Node, got %v", node.Remarks)
				}
				if node.AlterID != 0 {
					t.Errorf("Expected AlterID 0, got %v", node.AlterID)
				}
				if node.Cipher != "auto" {
					t.Errorf("Expected cipher auto, got %v", node.Cipher)
				}
				if node.Transport.Type != types.TransportWS {
					t.Errorf("Expected WS transport, got %v", node.Transport.Type)
				}
				if node.Transport.Host != "ws.example.com" {
					t.Errorf("Expected Host ws.example.com, got %v", node.Transport.Host)
				}
				if node.Transport.Path != "/ray" {
					t.Errorf("Expected Path /ray, got %v", node.Transport.Path)
				}
				if node.Security != types.SecurityTLS {
					t.Errorf("Expected TLS security, got %v", node.Security)
				}
				if node.TLS.ServerName != "sni.example.com" {
					t.Errorf("Expected SNI sni.example.com, got %v", node.TLS.ServerName)
				}
				if len(node.TLS.ALPN) != 1 || node.TLS.ALPN[0] != "h2" {
					t.Errorf("Expected ALPN h2, got %v", node.TLS.ALPN)
				}
				if node.TLS.Fingerprint != "chrome" {
					t.Errorf("Expected FP chrome, got %v", node.TLS.Fingerprint)
				}
			},
		},
		{
			name: "VMess JSON with string port and missing TLS (URL-Safe Base64 No Pad)",
			uri: makeB64(`{
				"v": "2",
				"ps": "StringPortNode",
				"add": "1.1.1.1",
				"port": "8080",
				"id": "uuid2",
				"aid": "64",
				"scy": "aes-128-gcm",
				"net": "tcp",
				"type": "http"
			}`, true, true), // URL-Safe and No Padding
			wantErr: false,
			check: func(t *testing.T, node *types.ProxyNode) {
				if node.Port != 8080 {
					t.Errorf("Expected port 8080 (parsed from string), got %v", node.Port)
				}
				if node.AlterID != 64 {
					t.Errorf("Expected AlterID 64 (parsed from string), got %v", node.AlterID)
				}
				if node.Transport.Type != types.TransportTCP {
					t.Errorf("Expected TCP transport, got %v", node.Transport.Type)
				}
				if node.Security != types.SecurityNone {
					t.Errorf("Expected None security, got %v", node.Security)
				}
			},
		},
		{
			name:    "Invalid Base64",
			uri:     "vmess://invalid_base64_string!@#",
			wantErr: true,
		},
		{
			name:    "Invalid JSON",
			uri:     makeB64(`{ "add": "abc" `, false, false),
			wantErr: true,
		},
		{
			name:    "Invalid Port Format",
			uri:     makeB64(`{"add": "1.1.1.1", "port": "abc", "id": "uuid"}`, false, false),
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseVMess(tt.uri)
			if (err != nil) != tt.wantErr {
				t.Errorf("parseVMess() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !tt.wantErr && tt.check != nil {
				tt.check(t, got)
			}
		})
	}
}

// TestParseInt checks the internal parseInt helper
func TestParseInt(t *testing.T) {
	tests := []struct {
		input   interface{}
		want    int
		wantErr bool
	}{
		{float64(443), 443, false},
		{"8080", 8080, false},
		{nil, 0, false},
		{"abc", 0, true},
		{true, 0, true},
	}
	for _, tt := range tests {
		got, err := parseInt(tt.input)
		if (err != nil) != tt.wantErr {
			t.Errorf("parseInt(%v) error = %v, wantErr %v", tt.input, err, tt.wantErr)
		}
		if got != tt.want {
			t.Errorf("parseInt(%v) = %v, want %v", tt.input, got, tt.want)
		}
	}
}

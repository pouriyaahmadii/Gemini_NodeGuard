package parser

import (
	"encoding/base64"
	"testing"
)

func TestParseURI(t *testing.T) {
	tests := []struct {
		name    string
		uri     string
		wantErr bool
	}{
		{
			name:    "VLESS valid",
			uri:     "vless://uuid@host:443",
			wantErr: false,
		},
		{
			name:    "VMess valid",
			uri:     "vmess://" + base64.StdEncoding.EncodeToString([]byte(`{"add":"host","port":443,"id":"uuid"}`)),
			wantErr: false,
		},
		{
			name:    "Trojan valid",
			uri:     "trojan://pass@host:443",
			wantErr: false,
		},
		{
			name:    "Shadowsocks not yet implemented/unsupported by URI parser currently", // Or modify logic to support it, but the plan only says 3 parsers
			uri:     "ss://YWVzLTEyOC1nY206dGVzdA==@192.168.100.1:8888#Example",
			wantErr: true,
		},
		{
			name:    "Empty",
			uri:     "   \t  ",
			wantErr: true,
		},
		{
			name:    "Random text",
			uri:     "not a uri",
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := ParseURI(tt.uri)
			if (err != nil) != tt.wantErr {
				t.Errorf("ParseURI() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestParseAll(t *testing.T) {
	lines := []string{
		"vless://uuid1@host1.com:443?type=tcp",
		"  ", // Empty line should be ignored
		"trojan://pass2@host2.com:443?type=ws&path=/chat",
		"vless://uuid1@host1.com:443?type=tcp", // Duplicate
		"vmess://invalid_base_64",              // Parse error
		"trojan://pass3@host3.com:80000",       // Invalid port (validation error handled by ParseURI for url parsing, or Validate)
		"trojan://@host4.com:443",              // Missing credentials (validation error)
	}

	nodes, errs := ParseAll(lines)

	if len(nodes) != 2 {
		t.Errorf("Expected 2 valid deduplicated nodes, got %d", len(nodes))
	}

	if len(errs) != 3 {
		t.Errorf("Expected 3 errors (invalid base64, invalid port parsing, missing credentials validation), got %d: %v", len(errs), errs)
	}

	// Verify order is preserved
	if len(nodes) == 2 {
		if nodes[0].Server != "host1.com" {
			t.Errorf("Expected first node server to be host1.com, got %s", nodes[0].Server)
		}
		if nodes[1].Server != "host2.com" {
			t.Errorf("Expected second node server to be host2.com, got %s", nodes[1].Server)
		}
	}
}

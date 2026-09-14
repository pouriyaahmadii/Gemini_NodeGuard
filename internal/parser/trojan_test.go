package parser

import (
	"testing"

	"gemini-sub-checker/internal/types"
)

func TestParseTrojan(t *testing.T) {
	tests := []struct {
		name    string
		uri     string
		wantErr bool
		check   func(*testing.T, *types.ProxyNode)
	}{
		{
			name:    "Standard Trojan with TLS",
			uri:     "trojan://myPassword123@example.com:443?security=tls&type=tcp&sni=sni.example.com&alpn=h2,http/1.1&fp=safari#TrojanNode",
			wantErr: false,
			check: func(t *testing.T, node *types.ProxyNode) {
				if node.Protocol != types.ProtocolTrojan {
					t.Errorf("Expected Trojan protocol, got %v", node.Protocol)
				}
				if node.UUID != "myPassword123" {
					t.Errorf("Expected UUID/password myPassword123, got %v", node.UUID)
				}
				if node.Server != "example.com" {
					t.Errorf("Expected server example.com, got %v", node.Server)
				}
				if node.Port != 443 {
					t.Errorf("Expected port 443, got %v", node.Port)
				}
				if node.Remarks != "TrojanNode" {
					t.Errorf("Expected remarks TrojanNode, got %v", node.Remarks)
				}
				if node.Transport.Type != types.TransportTCP {
					t.Errorf("Expected TCP transport, got %v", node.Transport.Type)
				}
				if node.Security != types.SecurityTLS {
					t.Errorf("Expected TLS security, got %v", node.Security)
				}
				if !node.TLS.Enabled {
					t.Errorf("Expected TLS to be enabled")
				}
				if node.TLS.ServerName != "sni.example.com" {
					t.Errorf("Expected SNI sni.example.com, got %v", node.TLS.ServerName)
				}
				if len(node.TLS.ALPN) != 2 || node.TLS.ALPN[0] != "h2" || node.TLS.ALPN[1] != "http/1.1" {
					t.Errorf("Expected ALPN h2,http/1.1, got %v", node.TLS.ALPN)
				}
				if node.TLS.Fingerprint != "safari" {
					t.Errorf("Expected FP safari, got %v", node.TLS.Fingerprint)
				}
			},
		},
		{
			name:    "Trojan gRPC Default Security",
			uri:     "trojan://password@grpc.com:8443?type=grpc&serviceName=TrojanService&peer=grpc.peer.com#gRPC+Node",
			wantErr: false,
			check: func(t *testing.T, node *types.ProxyNode) {
				if node.Transport.Type != types.TransportGRPC {
					t.Errorf("Expected GRPC transport, got %v", node.Transport.Type)
				}
				if node.Transport.ServiceName != "TrojanService" {
					t.Errorf("Expected ServiceName TrojanService, got %v", node.Transport.ServiceName)
				}
				if node.Security != types.SecurityTLS { // Default for trojan
					t.Errorf("Expected Default TLS security, got %v", node.Security)
				}
				if !node.TLS.Enabled {
					t.Errorf("Expected TLS to be enabled by default")
				}
				// Verify 'peer' maps to ServerName
				if node.TLS.ServerName != "grpc.peer.com" {
					t.Errorf("Expected SNI (mapped from peer) grpc.peer.com, got %v", node.TLS.ServerName)
				}
				if node.Remarks != "gRPC Node" { // + should decode to space
					t.Errorf("Expected unescaped remarks 'gRPC Node', got %v", node.Remarks)
				}
			},
		},
		{
			name:    "Trojan Missing Port",
			uri:     "trojan://password@no-port.com?type=tcp",
			wantErr: false,
			check: func(t *testing.T, node *types.ProxyNode) {
				if node.Port != 0 {
					t.Errorf("Expected port 0 when missing, got %v", node.Port)
				}
			},
		},
		{
			name:    "Trojan Invalid Port",
			uri:     "trojan://password@invalid-port.com:abc",
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseTrojan(tt.uri)
			if (err != nil) != tt.wantErr {
				t.Errorf("parseTrojan() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !tt.wantErr && tt.check != nil {
				tt.check(t, got)
			}
		})
	}
}

package parser

import (
	"testing"

	"gemini-nodeguard/internal/types"
)

func TestParseVLESS(t *testing.T) {
	tests := []struct {
		name    string
		uri     string
		wantErr bool
		check   func(*testing.T, *types.ProxyNode)
	}{
		{
			name:    "VLESS XTLS Vision",
			uri:     "vless://2a3e0f73-cc82-4140-ab94-d2e858dbf411@example.com:443?encryption=none&security=tls&type=tcp&headerType=none&sni=example.com&alpn=h2,http/1.1&flow=xtls-rprx-vision&fp=chrome#TestNode",
			wantErr: false,
			check: func(t *testing.T, node *types.ProxyNode) {
				if node.Protocol != types.ProtocolVless {
					t.Errorf("Expected VLESS protocol, got %v", node.Protocol)
				}
				if node.UUID != "2a3e0f73-cc82-4140-ab94-d2e858dbf411" {
					t.Errorf("Expected UUID 2a3e0f73-cc82-4140-ab94-d2e858dbf411, got %v", node.UUID)
				}
				if node.Server != "example.com" {
					t.Errorf("Expected server example.com, got %v", node.Server)
				}
				if node.Port != 443 {
					t.Errorf("Expected port 443, got %v", node.Port)
				}
				if node.Remarks != "TestNode" {
					t.Errorf("Expected remarks TestNode, got %v", node.Remarks)
				}
				if node.Flow != "xtls-rprx-vision" {
					t.Errorf("Expected flow xtls-rprx-vision, got %v", node.Flow)
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
				if node.TLS.ServerName != "example.com" {
					t.Errorf("Expected SNI example.com, got %v", node.TLS.ServerName)
				}
				if len(node.TLS.ALPN) != 2 || node.TLS.ALPN[0] != "h2" || node.TLS.ALPN[1] != "http/1.1" {
					t.Errorf("Expected ALPN h2,http/1.1, got %v", node.TLS.ALPN)
				}
				if node.TLS.Fingerprint != "chrome" {
					t.Errorf("Expected FP chrome, got %v", node.TLS.Fingerprint)
				}
			},
		},
		{
			name:    "VLESS Reality gRPC",
			uri:     "vless://uuid-test@192.168.1.1:8443?type=grpc&security=reality&serviceName=myService&sni=yahoo.com&pbk=publicKey123&sid=shortId456&spx=/spiderx&fp=safari#RealityNode",
			wantErr: false,
			check: func(t *testing.T, node *types.ProxyNode) {
				if node.Transport.Type != types.TransportGRPC {
					t.Errorf("Expected GRPC transport, got %v", node.Transport.Type)
				}
				if node.Transport.ServiceName != "myService" {
					t.Errorf("Expected serviceName myService, got %v", node.Transport.ServiceName)
				}
				if node.Security != types.SecurityReality {
					t.Errorf("Expected Reality security, got %v", node.Security)
				}
				if !node.Reality.Enabled {
					t.Errorf("Expected Reality to be enabled")
				}
				if node.Reality.PublicKey != "publicKey123" {
					t.Errorf("Expected Reality PBK publicKey123, got %v", node.Reality.PublicKey)
				}
				if node.Reality.ShortID != "shortId456" {
					t.Errorf("Expected Reality SID shortId456, got %v", node.Reality.ShortID)
				}
				if node.Reality.SpiderX != "/spiderx" {
					t.Errorf("Expected Reality SPX /spiderx, got %v", node.Reality.SpiderX)
				}
				if !node.TLS.Enabled {
					t.Errorf("Expected TLS to be enabled via Reality")
				}
				if node.TLS.ServerName != "yahoo.com" {
					t.Errorf("Expected SNI yahoo.com, got %v", node.TLS.ServerName)
				}
			},
		},
		{
			name:    "VLESS WS without TLS (allowInsecure parsing test)",
			uri:     "vless://uuid@domain:80?type=ws&path=%2Fchat&security=tls&allowInsecure=1#WSNode",
			wantErr: false,
			check: func(t *testing.T, node *types.ProxyNode) {
				if node.Transport.Type != types.TransportWS {
					t.Errorf("Expected WS transport, got %v", node.Transport.Type)
				}
				if node.Transport.Path != "/chat" {
					t.Errorf("Expected unescaped path /chat, got %v", node.Transport.Path)
				}
				if node.Security != types.SecurityTLS {
					t.Errorf("Expected TLS security, got %v", node.Security)
				}
				if !node.TLS.Insecure {
					t.Errorf("Expected TLS Insecure to be true via allowInsecure=1")
				}
			},
		},
		{
			name:    "Invalid Port",
			uri:     "vless://uuid@domain:abc",
			wantErr: true,
		},
		{
			name:    "Invalid URI scheme",
			uri:     "vmess://abc",
			wantErr: true, // vless parser function is passed vmess
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := parseVLESS(tt.uri)
			if (err != nil) != tt.wantErr {
				t.Errorf("parseVLESS() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !tt.wantErr && tt.check != nil {
				tt.check(t, got)
			}
		})
	}
}

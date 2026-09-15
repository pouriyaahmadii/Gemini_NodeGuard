package checker

import (
	"gemini-sub-checker/internal/types"
	"reflect"
	"testing"
)

func TestBuildSingboxOutbound(t *testing.T) {
	tests := []struct {
		name    string
		node    *types.ProxyNode
		want    map[string]any
		wantErr bool
	}{
		{
			name: "VLESS with Reality and Vision flow",
			node: &types.ProxyNode{
				Protocol: types.ProtocolVless,
				Server:   "example.com",
				Port:     443,
				UUID:     "test-uuid",
				Flow:     "xtls-rprx-vision",
				Security: types.SecurityReality,
				Reality: types.RealityConfig{
					Enabled:   true,
					PublicKey: "pubkey",
					ShortID:   "short",
					SpiderX:   "/sp",
				},
				TLS: types.TLSConfig{
					ServerName:  "sni.com",
					Fingerprint: "chrome",
				},
			},
			want: map[string]any{
				"type":        "vless",
				"tag":         "proxy",
				"server":      "example.com",
				"server_port": 443,
				"uuid":        "test-uuid",
				"flow":        "xtls-rprx-vision",
				"tls": map[string]any{
					"enabled":     true,
					"server_name": "sni.com",
					"utls": map[string]any{
						"enabled":     true,
						"fingerprint": "chrome",
					},
					"reality": map[string]any{
						"enabled":    true,
						"public_key": "pubkey",
						"short_id":   "short",
						"spider_x":   "/sp",
					},
				},
			},
			wantErr: false,
		},
		{
			name: "VMess with WebSocket and TLS",
			node: &types.ProxyNode{
				Protocol: types.ProtocolVmess,
				Server:   "ws.com",
				Port:     8443,
				UUID:     "vmess-uuid",
				AlterID:  0,
				Cipher:   "auto",
				Security: types.SecurityTLS,
				TLS: types.TLSConfig{
					Enabled:    true,
					ServerName: "ws.com",
				},
				Transport: types.TransportConfig{
					Type: types.TransportWS,
					Path: "/ws",
					Host: "ws.com",
				},
			},
			want: map[string]any{
				"type":        "vmess",
				"tag":         "proxy",
				"server":      "ws.com",
				"server_port": 8443,
				"uuid":        "vmess-uuid",
				"alter_id":    0,
				"security":    "auto",
				"tls": map[string]any{
					"enabled":     true,
					"server_name": "ws.com",
				},
				"transport": map[string]any{
					"type": "ws",
					"path": "/ws",
					"headers": map[string]string{
						"Host": "ws.com",
					},
				},
			},
			wantErr: false,
		},
		{
			name: "Trojan with gRPC and SNI",
			node: &types.ProxyNode{
				Protocol: types.ProtocolTrojan,
				Server:   "grpc.com",
				Port:     443,
				UUID:     "password",
				Security: types.SecurityTLS,
				TLS: types.TLSConfig{
					Enabled:    true,
					ServerName: "grpc.com",
					ALPN:       []string{"h2"},
				},
				Transport: types.TransportConfig{
					Type:        types.TransportGRPC,
					ServiceName: "ServiceGRPC",
				},
			},
			want: map[string]any{
				"type":        "trojan",
				"tag":         "proxy",
				"server":      "grpc.com",
				"server_port": 443,
				"password":    "password",
				"tls": map[string]any{
					"enabled":     true,
					"server_name": "grpc.com",
					"alpn":        []string{"h2"},
				},
				"transport": map[string]any{
					"type":         "grpc",
					"service_name": "ServiceGRPC",
				},
			},
			wantErr: false,
		},
		{
			name: "Unsupported Protocol",
			node: &types.ProxyNode{
				Protocol: "unknown",
			},
			want:    nil,
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := BuildSingboxOutbound(tt.node)
			if (err != nil) != tt.wantErr {
				t.Errorf("BuildSingboxOutbound() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("BuildSingboxOutbound() got = %v, want %v", got, tt.want)
			}
		})
	}
}

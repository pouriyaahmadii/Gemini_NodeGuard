package types

import (
	"errors"
	"testing"
)

func TestProxyNode_Validate(t *testing.T) {
	tests := []struct {
		name    string
		node    ProxyNode
		wantErr error
	}{
		{
			name: "valid vless node",
			node: ProxyNode{
				Protocol: ProtocolVless,
				Server:   "example.com",
				Port:     443,
				UUID:     "12345678-1234-1234-1234-123456789abc",
			},
			wantErr: nil,
		},
		{
			name: "valid shadowsocks node",
			node: ProxyNode{
				Protocol: ProtocolShadowsocks,
				Server:   "example.com",
				Port:     8388,
				UUID:     "password123",
				Cipher:   "chacha20-poly1305",
			},
			wantErr: nil,
		},
		{
			name: "empty server",
			node: ProxyNode{
				Protocol: ProtocolVless,
				Server:   "  ",
				Port:     443,
				UUID:     "1234",
			},
			wantErr: ErrEmptyServer,
		},
		{
			name: "invalid port low",
			node: ProxyNode{
				Protocol: ProtocolVless,
				Server:   "example.com",
				Port:     0,
				UUID:     "1234",
			},
			wantErr: ErrInvalidPort,
		},
		{
			name: "invalid port high",
			node: ProxyNode{
				Protocol: ProtocolVless,
				Server:   "example.com",
				Port:     65536,
				UUID:     "1234",
			},
			wantErr: ErrInvalidPort,
		},
		{
			name: "invalid protocol",
			node: ProxyNode{
				Protocol: "invalid",
				Server:   "example.com",
				Port:     443,
				UUID:     "1234",
			},
			wantErr: ErrInvalidProtocol,
		},
		{
			name: "missing credentials",
			node: ProxyNode{
				Protocol: ProtocolVless,
				Server:   "example.com",
				Port:     443,
				UUID:     "  ",
			},
			wantErr: ErrMissingCredentials,
		},
		{
			name: "missing cipher for shadowsocks",
			node: ProxyNode{
				Protocol: ProtocolShadowsocks,
				Server:   "example.com",
				Port:     8388,
				UUID:     "password123",
				Cipher:   "  ",
			},
			wantErr: ErrMissingCipher,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.node.Validate()
			if !errors.Is(err, tt.wantErr) {
				t.Errorf("Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestProxyNode_DedupKey(t *testing.T) {
	tests := []struct {
		name string
		node ProxyNode
		want string
	}{
		{
			name: "base case no params",
			node: ProxyNode{
				Protocol: ProtocolVless,
				Server:   "example.com",
				Port:     443,
			},
			want: "vless://example.com:443",
		},
		{
			name: "with uuid only",
			node: ProxyNode{
				Protocol: ProtocolVless,
				Server:   "example.com",
				Port:     443,
				UUID:     "123",
			},
			want: "vless://example.com:443?id=123",
		},
		{
			name: "with all deduplication fields",
			node: ProxyNode{
				Protocol: ProtocolTrojan,
				Server:   "example.com",
				Port:     443,
				UUID:     "pass",
				TLS: TLSConfig{
					ServerName: "sni.com",
				},
				Transport: TransportConfig{
					Type: TransportWS,
					Path: "/path",
				},
			},
			want: "trojan://example.com:443?id=pass&path=%2Fpath&sni=sni.com&type=ws",
		},
		{
			name: "with empty strings omitted",
			node: ProxyNode{
				Protocol: ProtocolVmess,
				Server:   "server",
				Port:     80,
				UUID:     "uuid",
				TLS: TLSConfig{
					ServerName: "",
				},
				Transport: TransportConfig{
					Type: TransportTCP,
					Path: "",
				},
			},
			want: "vmess://server:80?id=uuid&type=tcp",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.node.DedupKey(); got != tt.want {
				t.Errorf("DedupKey() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestProxyNode_DisplayName(t *testing.T) {
	tests := []struct {
		name string
		node ProxyNode
		want string
	}{
		{
			name: "normal remarks",
			node: ProxyNode{
				Remarks: "My Node",
				Server:  "example.com",
				Port:    443,
			},
			want: "My Node",
		},
		{
			name: "whitespace remarks",
			node: ProxyNode{
				Remarks: "  My Node  ",
				Server:  "example.com",
				Port:    443,
			},
			want: "My Node",
		},
		{
			name: "remarks with newlines",
			node: ProxyNode{
				Remarks: "My\nNode\r2",
				Server:  "example.com",
				Port:    443,
			},
			want: "MyNode2", // Control chars stripped
		},
		{
			name: "empty remarks fallback",
			node: ProxyNode{
				Remarks: "",
				Server:  "example.com",
				Port:    443,
			},
			want: "example.com:443",
		},
		{
			name: "spaces only remarks fallback",
			node: ProxyNode{
				Remarks: "   ",
				Server:  "example.com",
				Port:    443,
			},
			want: "example.com:443",
		},
		{
			name: "control chars only remarks fallback",
			node: ProxyNode{
				Remarks: "\n\t",
				Server:  "example.com",
				Port:    443,
			},
			want: "example.com:443",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.node.DisplayName(); got != tt.want {
				t.Errorf("DisplayName() = %v, want %v", got, tt.want)
			}
		})
	}
}

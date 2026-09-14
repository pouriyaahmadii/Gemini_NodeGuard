package fetcher

import (
	"encoding/base64"
	"reflect"
	"testing"
)

func TestDecodeSubscription(t *testing.T) {
	tests := []struct {
		name    string
		input   []byte
		want    []string
		wantErr bool
	}{
		{
			name:    "Standard Base64 with padding",
			input:   []byte(base64.StdEncoding.EncodeToString([]byte("vless://node1\nvmess://node2"))),
			want:    []string{"vless://node1", "vmess://node2"},
			wantErr: false,
		},
		{
			name:    "Raw Standard Base64 (no padding)",
			input:   []byte(base64.RawStdEncoding.EncodeToString([]byte("vless://node1\nvmess://node2"))),
			want:    []string{"vless://node1", "vmess://node2"},
			wantErr: false,
		},
		{
			name:    "URL-safe Base64 with padding",
			input:   []byte(base64.URLEncoding.EncodeToString([]byte("vless://node_1\nvmess://node-2"))),
			want:    []string{"vless://node_1", "vmess://node-2"},
			wantErr: false,
		},
		{
			name:    "Raw URL-safe Base64 (no padding)",
			input:   []byte(base64.RawURLEncoding.EncodeToString([]byte("vless://node_1\nvmess://node-2"))),
			want:    []string{"vless://node_1", "vmess://node-2"},
			wantErr: false,
		},
		{
			name: "Base64 with embedded line breaks and whitespace",
			// "vless://node1" -> base64.StdEncoding is "dmxlc3M6Ly9ub2RlMQ=="
			input:   []byte("dmxl\r\nc3M6\t\n Ly9ub2\n\n RlMQ=="),
			want:    []string{"vless://node1"},
			wantErr: false,
		},
		{
			name:    "Plaintext input with multiple URI schemes",
			input:   []byte("vless://node1\n\nvmess://node2\n# comment\n// another comment\ntrojan://node3"),
			want:    []string{"vless://node1", "vmess://node2", "trojan://node3"},
			wantErr: false,
		},
		{
			name:    "Empty input",
			input:   []byte(""),
			want:    []string{},
			wantErr: false,
		},
		{
			name:    "Whitespace only input",
			input:   []byte("   \n\t  \r\n "),
			want:    []string{},
			wantErr: false,
		},
		{
			name:    "BOM prefix handling",
			input:   append([]byte("\xef\xbb\xbf"), []byte("vless://node1\nvmess://node2")...),
			want:    []string{"vless://node1", "vmess://node2"},
			wantErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := DecodeSubscription(tt.input)
			if (err != nil) != tt.wantErr {
				t.Errorf("DecodeSubscription() error = %v, wantErr %v", err, tt.wantErr)
				return
			}

			// nil slices and empty slices should be treated as equal for this test
			if len(got) == 0 && len(tt.want) == 0 {
				return
			}

			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("DecodeSubscription() = %v, want %v", got, tt.want)
			}
		})
	}
}

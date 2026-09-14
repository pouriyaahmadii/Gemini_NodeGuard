package types

import (
	"errors"
	"fmt"
	"net/url"
	"strings"
	"time"
	"unicode"
)

// Protocol represents the supported V2Ray protocols.
type Protocol string

const (
	ProtocolVless       Protocol = "vless"
	ProtocolVmess       Protocol = "vmess"
	ProtocolTrojan      Protocol = "trojan"
	ProtocolShadowsocks Protocol = "shadowsocks"
	ProtocolHysteria2   Protocol = "hysteria2"
)

// TransportType represents the supported transport layers.
type TransportType string

const (
	TransportTCP         TransportType = "tcp"
	TransportWS          TransportType = "ws"
	TransportGRPC        TransportType = "grpc"
	TransportHTTPUpgrade TransportType = "httpupgrade"
	TransportSplitHTTP   TransportType = "splithttp"
	TransportH2          TransportType = "h2"
)

// SecurityType represents the supported security types.
type SecurityType string

const (
	SecurityNone    SecurityType = "none"
	SecurityTLS     SecurityType = "tls"
	SecurityReality SecurityType = "reality"
)

// TLSConfig holds configuration for TLS-based connections.
type TLSConfig struct {
	Enabled     bool     `json:"enabled"`
	ServerName  string   `json:"server_name"`
	ALPN        []string `json:"alpn,omitempty"`
	Insecure    bool     `json:"insecure"`
	Fingerprint string   `json:"fingerprint,omitempty"` // chrome, firefox, safari, etc.
}

// RealityConfig holds configuration for XTLS Reality.
type RealityConfig struct {
	Enabled   bool   `json:"enabled"`
	PublicKey string `json:"public_key"`
	ShortID   string `json:"short_id,omitempty"`
	SpiderX   string `json:"spider_x,omitempty"`
}

// TransportConfig holds transport layer settings.
type TransportConfig struct {
	Type        TransportType     `json:"type"`
	Host        string            `json:"host,omitempty"`
	Path        string            `json:"path,omitempty"`
	ServiceName string            `json:"service_name,omitempty"` // For gRPC
	Headers     map[string]string `json:"headers,omitempty"`
}

// ProxyNode represents a generic proxy node with standard configuration.
type ProxyNode struct {
	ID                 string          `json:"id"`
	Remarks            string          `json:"remarks"`
	Protocol           Protocol        `json:"protocol"`
	Server             string          `json:"server"`
	Port               int             `json:"port"`
	UUID               string          `json:"uuid,omitempty"`     // UUID or Password
	Cipher             string          `json:"cipher,omitempty"`   // e.g. auto, chacha20-poly1305
	AlterID            int             `json:"alter_id,omitempty"` // VMess legacy compatibility
	Flow               string          `json:"flow,omitempty"`     // e.g. xtls-rprx-vision
	Security           SecurityType    `json:"security"`
	TLS                TLSConfig       `json:"tls,omitempty"`
	Reality            RealityConfig   `json:"reality,omitempty"`
	Transport          TransportConfig `json:"transport,omitempty"`
	RawURI             string          `json:"raw_uri"`
	IsGeminiCompatible bool            `json:"is_gemini_compatible"`
	Latency            time.Duration   `json:"latency"`
}

// Validation errors.
var (
	ErrEmptyServer        = errors.New("server cannot be empty")
	ErrInvalidPort        = errors.New("port must be between 1 and 65535")
	ErrInvalidProtocol    = errors.New("invalid or unsupported protocol")
	ErrMissingCredentials = errors.New("missing authentication credentials (UUID/Password)")
	ErrMissingCipher      = errors.New("missing cipher for shadowsocks protocol")
)

// Validate checks the node configuration for correctness.
func (n *ProxyNode) Validate() error {
	if strings.TrimSpace(n.Server) == "" {
		return ErrEmptyServer
	}
	if n.Port < 1 || n.Port > 65535 {
		return ErrInvalidPort
	}
	switch n.Protocol {
	case ProtocolVless, ProtocolVmess, ProtocolTrojan, ProtocolShadowsocks, ProtocolHysteria2:
		// Valid protocol
	default:
		return ErrInvalidProtocol
	}

	if strings.TrimSpace(n.UUID) == "" {
		return ErrMissingCredentials
	}

	if n.Protocol == ProtocolShadowsocks && strings.TrimSpace(n.Cipher) == "" {
		return ErrMissingCipher
	}

	return nil
}

// DedupKey generates a canonical string for deduplication purposes.
// Format: <protocol>://<server>:<port>?<sorted_params>
func (n *ProxyNode) DedupKey() string {
	q := url.Values{}
	if n.UUID != "" {
		q.Set("id", n.UUID)
	}
	if n.TLS.ServerName != "" {
		q.Set("sni", n.TLS.ServerName)
	}
	if n.Transport.Path != "" {
		q.Set("path", n.Transport.Path)
	}
	if n.Transport.Type != "" {
		q.Set("type", string(n.Transport.Type))
	}

	queryString := q.Encode()
	base := fmt.Sprintf("%s://%s:%d", n.Protocol, n.Server, n.Port)
	if queryString != "" {
		return base + "?" + queryString
	}
	return base
}

// DisplayName returns a sanitized string for displaying the node,
// falling back to <server>:<port> if remarks are empty after sanitization.
func (n *ProxyNode) DisplayName() string {
	// Sanitize remarks
	sanitized := strings.Map(func(r rune) rune {
		if unicode.IsControl(r) {
			return -1
		}
		return r
	}, n.Remarks)

	sanitized = strings.TrimSpace(sanitized)

	if sanitized == "" {
		return fmt.Sprintf("%s:%d", n.Server, n.Port)
	}
	return sanitized
}

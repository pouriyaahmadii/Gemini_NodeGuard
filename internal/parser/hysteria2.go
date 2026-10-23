package parser

import (
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"

	"gemini-nodeguard/internal/types"
)

// parseHysteria2 parses a hysteria2:// URI string into a ProxyNode.
// Format: hysteria2://<password>@<host>:<port>/?sni=<sni>&insecure=1&obfs=<type>&obfs-password=<pass>#<remark>
func parseHysteria2(rawURI string) (*types.ProxyNode, error) {
	if !strings.HasPrefix(rawURI, "hysteria2://") && !strings.HasPrefix(rawURI, "hy2://") {
		return nil, errors.New("invalid hysteria2 uri")
	}

	// Normalize to hysteria2://
	if strings.HasPrefix(rawURI, "hy2://") {
		rawURI = "hysteria2://" + rawURI[6:]
	}

	u, err := url.Parse(rawURI)
	if err != nil {
		return nil, fmt.Errorf("failed to parse hysteria2 URL: %w", err)
	}

	node := types.NewProxyNode()
	node.Protocol = types.ProtocolHysteria2
	node.RawURI = rawURI

	if u.User != nil {
		node.UUID = u.User.String()
	}

	node.Server = u.Hostname()
	portStr := u.Port()
	if portStr != "" {
		port, err := strconv.Atoi(portStr)
		if err != nil {
			return nil, fmt.Errorf("invalid port format: %w", err)
		}
		node.Port = port
	}

	q := u.Query()

	// SNI and TLS
	sni := q.Get("sni")
	node.Security = types.SecurityTLS
	node.TLS.Enabled = true
	if sni != "" {
		node.TLS.ServerName = sni
	} else {
		node.TLS.ServerName = node.Server
	}

	if q.Get("insecure") == "1" {
		node.TLS.Insecure = true
	}

	// Obfs
	obfsType := q.Get("obfs")
	obfsPass := q.Get("obfs-password")
	if obfsType != "" {
		node.Transport.ObfsType = obfsType
		node.Transport.ObfsPassword = obfsPass
	}

	// Remarks
	node.Remarks, _ = url.QueryUnescape(u.Fragment)

	return node, nil
}

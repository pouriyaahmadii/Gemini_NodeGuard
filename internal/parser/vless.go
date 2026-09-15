package parser

import (
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"

	"gemini-sub-checker/internal/types"
)

// parseVLESS parses a vless:// URI string into a ProxyNode.
func parseVLESS(rawURI string) (*types.ProxyNode, error) {
	if !strings.HasPrefix(rawURI, "vless://") {
		return nil, errors.New("invalid vless uri")
	}

	u, err := url.Parse(rawURI)
	if err != nil {
		return nil, fmt.Errorf("failed to parse vless uri: %w", err)
	}

	node := &types.ProxyNode{
		Protocol: types.ProtocolVless,
		RawURI:   rawURI,
	}

	if u.User != nil {
		node.UUID = u.User.Username()
	}

	node.Server = u.Hostname()
	portStr := u.Port()
	if portStr != "" {
		port, err := strconv.Atoi(portStr)
		if err != nil {
			return nil, fmt.Errorf("invalid port: %w", err)
		}
		node.Port = port
	}

	if u.Fragment != "" {
		raw := u.RawFragment
		if raw == "" {
			raw = u.Fragment // fallback if not encoded
		}
		remarks, err := url.QueryUnescape(raw)
		if err == nil {
			node.Remarks = remarks
		} else {
			node.Remarks = u.Fragment
		}
	}

	q := u.Query()

	// Parse flow
	node.Flow = q.Get("flow")

	// Parse Transport
	transportType := q.Get("type")
	if transportType == "" {
		transportType = q.Get("transport") // fallback for some old formats if any
	}
	if transportType == "" {
		transportType = "tcp"
	}
	node.Transport.Type = types.TransportType(transportType)

	if path := q.Get("path"); path != "" {
		unescapedPath, err := url.QueryUnescape(path)
		if err == nil {
			node.Transport.Path = unescapedPath
		} else {
			node.Transport.Path = path
		}
	}
	node.Transport.Host = q.Get("host")
	node.Transport.ServiceName = q.Get("serviceName")

	// Parse Security
	security := q.Get("security")
	switch security {
	case "tls":
		node.Security = types.SecurityTLS
		node.TLS.Enabled = true
	case "reality":
		node.Security = types.SecurityReality
		node.TLS.Enabled = true // Reality implies TLS
		node.Reality.Enabled = true
	case "none", "":
		node.Security = types.SecurityNone
	default:
		node.Security = types.SecurityType(security)
	}

	// TLS Options
	if node.TLS.Enabled {
		node.TLS.ServerName = q.Get("sni")
		if alpn := q.Get("alpn"); alpn != "" {
			node.TLS.ALPN = strings.Split(alpn, ",")
		}
		node.TLS.Fingerprint = q.Get("fp")
		insecure := q.Get("allowInsecure")
		if insecure == "" {
			insecure = q.Get("insecure")
		}
		if insecure == "1" || insecure == "true" {
			node.TLS.Insecure = true
		}
	}

	// Reality Options
	if node.Reality.Enabled {
		node.Reality.PublicKey = q.Get("pbk")
		node.Reality.ShortID = q.Get("sid")
		node.Reality.SpiderX = q.Get("spx")
	}

	return node, nil
}

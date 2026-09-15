package checker

import (
	"fmt"
	"gemini-sub-checker/internal/types"
)

// BuildSingboxOutbound generates a sing-box outbound configuration map from a ProxyNode.
func BuildSingboxOutbound(node *types.ProxyNode) (map[string]any, error) {
	out := map[string]any{
		"type":        string(node.Protocol),
		"tag":         "proxy",
		"server":      node.Server,
		"server_port": node.Port,
	}

	switch node.Protocol {
	case types.ProtocolVless:
		out["uuid"] = node.UUID
		if node.Flow != "" {
			out["flow"] = node.Flow
		}
	case types.ProtocolVmess:
		out["uuid"] = node.UUID
		out["alter_id"] = node.AlterID
		if node.Cipher != "" {
			out["security"] = node.Cipher
		} else {
			out["security"] = "auto"
		}
	case types.ProtocolTrojan:
		out["password"] = node.UUID
	default:
		return nil, fmt.Errorf("unsupported protocol for sing-box outbound: %s", node.Protocol)
	}

	// TLS configuration
	if node.TLS.Enabled || node.Security == types.SecurityTLS || node.Security == types.SecurityReality {
		tlsConfig := map[string]any{
			"enabled": true,
		}
		if node.TLS.ServerName != "" {
			tlsConfig["server_name"] = node.TLS.ServerName
		}
		if len(node.TLS.ALPN) > 0 {
			tlsConfig["alpn"] = node.TLS.ALPN
		}
		if node.TLS.Insecure {
			tlsConfig["insecure"] = true
		}
		if node.TLS.Fingerprint != "" {
			tlsConfig["utls"] = map[string]any{
				"enabled":     true,
				"fingerprint": node.TLS.Fingerprint,
			}
		}

		// Reality configuration
		if node.Security == types.SecurityReality && node.Reality.Enabled {
			realityConfig := map[string]any{
				"enabled":    true,
				"public_key": node.Reality.PublicKey,
			}
			if node.Reality.ShortID != "" {
				realityConfig["short_id"] = node.Reality.ShortID
			}
			if node.Reality.SpiderX != "" {
				// spider_x is usually passed in reality config for sing-box (though rarely used now).
				realityConfig["spider_x"] = node.Reality.SpiderX
			}
			tlsConfig["reality"] = realityConfig
		}

		out["tls"] = tlsConfig
	}

	// Transport configuration
	if node.Transport.Type != "" && node.Transport.Type != types.TransportTCP {
		transportConfig := map[string]any{
			"type": string(node.Transport.Type),
		}
		if node.Transport.Path != "" {
			transportConfig["path"] = node.Transport.Path
		}

		var headers map[string]string
		if node.Transport.Host != "" || len(node.Transport.Headers) > 0 {
			headers = make(map[string]string)
			if node.Transport.Host != "" {
				headers["Host"] = node.Transport.Host
			}
			for k, v := range node.Transport.Headers {
				headers[k] = v
			}
			transportConfig["headers"] = headers
		}

		if node.Transport.Type == types.TransportGRPC && node.Transport.ServiceName != "" {
			transportConfig["service_name"] = node.Transport.ServiceName
		}

		out["transport"] = transportConfig
	}

	return out, nil
}

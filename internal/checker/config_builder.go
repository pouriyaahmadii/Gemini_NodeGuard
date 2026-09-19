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

// BuildXrayOutbound generates an xray outbound configuration map from a ProxyNode.
func BuildXrayOutbound(node *types.ProxyNode) (map[string]any, error) {
	out := map[string]any{
		"tag": "proxy",
	}

	settings := map[string]any{}

	switch node.Protocol {
	case types.ProtocolVless:
		out["protocol"] = "vless"
		vnext := map[string]any{
			"address": node.Server,
			"port":    node.Port,
			"users": []map[string]any{
				{
					"id":         node.UUID,
					"encryption": "none",
				},
			},
		}
		if node.Flow != "" {
			vnext["users"].([]map[string]any)[0]["flow"] = node.Flow
		}
		settings["vnext"] = []map[string]any{vnext}

	case types.ProtocolVmess:
		out["protocol"] = "vmess"
		vnext := map[string]any{
			"address": node.Server,
			"port":    node.Port,
			"users": []map[string]any{
				{
					"id":       node.UUID,
					"alterId":  node.AlterID,
					"security": node.Cipher,
				},
			},
		}
		if node.Cipher == "" {
			vnext["users"].([]map[string]any)[0]["security"] = "auto"
		}
		settings["vnext"] = []map[string]any{vnext}

	case types.ProtocolTrojan:
		out["protocol"] = "trojan"
		servers := map[string]any{
			"address":  node.Server,
			"port":     node.Port,
			"password": node.UUID,
		}
		settings["servers"] = []map[string]any{servers}

	default:
		return nil, fmt.Errorf("unsupported protocol for xray outbound: %s", node.Protocol)
	}

	out["settings"] = settings

	// streamSettings
	streamSettings := map[string]any{}

	// Transport configuration
	if node.Transport.Type != "" && node.Transport.Type != types.TransportTCP {
		streamSettings["network"] = string(node.Transport.Type)

		switch node.Transport.Type {
		case types.TransportWS:
			wsSettings := map[string]any{}
			if node.Transport.Path != "" {
				wsSettings["path"] = node.Transport.Path
			}
			if node.Transport.Host != "" || len(node.Transport.Headers) > 0 {
				headers := make(map[string]string)
				if node.Transport.Host != "" {
					headers["Host"] = node.Transport.Host
				}
				for k, v := range node.Transport.Headers {
					headers[k] = v
				}
				wsSettings["headers"] = headers
			}
			streamSettings["wsSettings"] = wsSettings

		case types.TransportGRPC:
			grpcSettings := map[string]any{}
			if node.Transport.ServiceName != "" {
				grpcSettings["serviceName"] = node.Transport.ServiceName
			}
			// Multi mode is default to false in xray if not specified, but we can set it.
			grpcSettings["multiMode"] = false
			streamSettings["grpcSettings"] = grpcSettings
		}
	} else {
		streamSettings["network"] = "tcp"
	}

	// TLS configuration
	if node.TLS.Enabled || node.Security == types.SecurityTLS || node.Security == types.SecurityReality {
		if node.Security == types.SecurityReality {
			streamSettings["security"] = "reality"
			realitySettings := map[string]any{
				"publicKey": node.Reality.PublicKey,
			}
			if node.TLS.ServerName != "" {
				realitySettings["serverName"] = node.TLS.ServerName
			}
			if node.Reality.ShortID != "" {
				realitySettings["shortId"] = node.Reality.ShortID
			}
			if node.Reality.SpiderX != "" {
				realitySettings["spiderX"] = node.Reality.SpiderX
			}
			if node.TLS.Fingerprint != "" {
				realitySettings["fingerprint"] = node.TLS.Fingerprint
			} else {
				realitySettings["fingerprint"] = "chrome"
			}
			streamSettings["realitySettings"] = realitySettings
		} else {
			streamSettings["security"] = "tls"
			tlsSettings := map[string]any{}
			if node.TLS.ServerName != "" {
				tlsSettings["serverName"] = node.TLS.ServerName
			}
			if len(node.TLS.ALPN) > 0 {
				tlsSettings["alpn"] = node.TLS.ALPN
			}
			if node.TLS.Insecure {
				tlsSettings["allowInsecure"] = true
			}
			if node.TLS.Fingerprint != "" {
				tlsSettings["fingerprint"] = node.TLS.Fingerprint
			}
			streamSettings["tlsSettings"] = tlsSettings
		}
	}

	out["streamSettings"] = streamSettings

	return out, nil
}

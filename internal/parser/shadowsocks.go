package parser

import (
	"encoding/base64"
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"

	"gemini-nodeguard/internal/types"
)

// parseShadowsocks parses an ss:// URI string into a ProxyNode.
// Format: ss://base64(method:password)@hostname:port#remarks
// Or: ss://method:password@hostname:port#remarks (though base64 is standard)
func parseShadowsocks(rawURI string) (*types.ProxyNode, error) {
	if !strings.HasPrefix(rawURI, "ss://") {
		return nil, errors.New("invalid shadowsocks uri")
	}

	u, err := url.Parse(rawURI)
	if err != nil {
		// Fallback for legacy SIP002 URIs that might be entirely base64 encoded
		// e.g., ss://BASE64
		b64Part := rawURI[5:]
		if idx := strings.Index(b64Part, "#"); idx != -1 {
			b64Part = b64Part[:idx]
		}

		// Add padding if missing
		if pad := len(b64Part) % 4; pad != 0 {
			b64Part += strings.Repeat("=", 4-pad)
		}

		decoded, err := base64.URLEncoding.DecodeString(b64Part)
		if err != nil {
			decoded, err = base64.StdEncoding.DecodeString(b64Part)
		}

		if err == nil {
			// Decoded should look like method:password@hostname:port
			return parseShadowsocks("ss://" + string(decoded))
		}

		return nil, fmt.Errorf("failed to parse shadowsocks uri: %w", err)
	}

	node := types.NewProxyNode()
	node.Protocol = types.ProtocolShadowsocks
	node.RawURI = rawURI

	// Extract server and port
	node.Server = u.Hostname()
	portStr := u.Port()
	if portStr != "" {
		port, err := strconv.Atoi(portStr)
		if err != nil {
			return nil, fmt.Errorf("invalid port format: %w", err)
		}
		node.Port = port
	}

	// Extract credentials. It could be user:pass or base64(user:pass) in u.User
	var methodAndPass string
	if u.User != nil {
		pass, hasPass := u.User.Password()
		if hasPass {
			methodAndPass = u.User.Username() + ":" + pass
		} else {
			// Might be base64 encoded user info
			b64Info := u.User.Username()

			// Add padding if missing
			if pad := len(b64Info) % 4; pad != 0 {
				b64Info += strings.Repeat("=", 4-pad)
			}

			decoded, err := base64.URLEncoding.DecodeString(b64Info)
			if err != nil {
				decoded, err = base64.StdEncoding.DecodeString(b64Info)
			}

			if err == nil {
				methodAndPass = string(decoded)
			} else {
				methodAndPass = b64Info // fallback, might be plain text
			}
		}
	}

	parts := strings.SplitN(methodAndPass, ":", 2)
	if len(parts) == 2 {
		node.Cipher = parts[0]
		node.UUID = parts[1] // For SS, UUID stores the password
	} else {
		return nil, errors.New("invalid shadowsocks credentials format")
	}

	// Extract remarks
	if u.Fragment != "" {
		remarks, err := url.QueryUnescape(u.Fragment)
		if err != nil {
			node.Remarks = u.Fragment
		} else {
			node.Remarks = remarks
		}
	}

	// SS usually uses TCP. (UDP is supported by cores, but for our HTTP probing TCP is what matters)
	node.Transport.Type = types.TransportTCP
	node.Security = types.SecurityNone

	return node, nil
}

package parser

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"gemini-sub-checker/internal/types"
)

// vmessJSON represents the JSON structure in a vmess URI payload.
type vmessJSON struct {
	V    interface{} `json:"v"`
	Ps   string      `json:"ps"`
	Add  string      `json:"add"`
	Port interface{} `json:"port"`
	ID   string      `json:"id"`
	Aid  interface{} `json:"aid"`
	Scy  string      `json:"scy"`
	Net  string      `json:"net"`
	Type string      `json:"type"`
	Host string      `json:"host"`
	Path string      `json:"path"`
	TLS  string      `json:"tls"`
	Sni  string      `json:"sni"`
	Alpn string      `json:"alpn"`
	Fp   string      `json:"fp"`
}

// parseInt converts an interface{} (which might be a string or float64 from JSON) to an int.
func parseInt(v interface{}) (int, error) {
	switch val := v.(type) {
	case float64:
		return int(val), nil
	case string:
		return strconv.Atoi(val)
	case nil:
		return 0, nil
	default:
		return 0, fmt.Errorf("unexpected type for integer field: %T", v)
	}
}

// parseVMess parses a vmess:// URI string into a ProxyNode.
func parseVMess(rawURI string) (*types.ProxyNode, error) {
	if !strings.HasPrefix(rawURI, "vmess://") {
		return nil, errors.New("invalid vmess uri")
	}

	b64Payload := rawURI[8:]

	// Add padding if missing
	if pad := len(b64Payload) % 4; pad != 0 {
		b64Payload += strings.Repeat("=", 4-pad)
	}

	// Try standard base64 decoding first, then URL-safe
	decoded, err := base64.StdEncoding.DecodeString(b64Payload)
	if err != nil {
		decoded, err = base64.URLEncoding.DecodeString(b64Payload)
		if err != nil {
			return nil, fmt.Errorf("failed to decode vmess base64: %w", err)
		}
	}

	var v vmessJSON
	if err := json.Unmarshal(decoded, &v); err != nil {
		return nil, fmt.Errorf("failed to unmarshal vmess json: %w", err)
	}

	node := &types.ProxyNode{
		Protocol: types.ProtocolVmess,
		RawURI:   rawURI,
	}

	node.Server = v.Add
	node.UUID = v.ID
	node.Remarks = v.Ps
	node.Cipher = v.Scy

	if v.Port != nil {
		port, err := parseInt(v.Port)
		if err != nil {
			return nil, fmt.Errorf("invalid port format: %w", err)
		}
		node.Port = port
	}

	if v.Aid != nil {
		aid, err := parseInt(v.Aid)
		if err == nil {
			node.AlterID = aid
		}
	}

	node.Transport.Type = types.TransportType(v.Net)
	if node.Transport.Type == "" {
		node.Transport.Type = types.TransportTCP
	}

	node.Transport.Host = v.Host
	node.Transport.Path = v.Path

	if v.Type != "" && v.Type != "none" && node.Transport.Type == types.TransportTCP {
		// Handle vmess HTTP obfs via type field
		// Often type="http" implies an HTTP header, but sometimes it just represents general obfuscation.
		// Usually we map headers if we parse the full config, but for standard simplified vmess JSON,
		// type="http" under net="tcp" means TCP with HTTP obfuscation.
		// types.TransportConfig doesn't have a direct field for tcp type, but we can set it if needed.
		// For now, mapping v.Type is typically not directly in types.TransportConfig unless we add Obfs field.
	}

	if v.TLS == "tls" {
		node.Security = types.SecurityTLS
		node.TLS.Enabled = true
		node.TLS.ServerName = v.Sni
		node.TLS.Fingerprint = v.Fp
		if v.Alpn != "" {
			node.TLS.ALPN = strings.Split(v.Alpn, ",")
		}
	} else {
		node.Security = types.SecurityNone
	}

	return node, nil
}

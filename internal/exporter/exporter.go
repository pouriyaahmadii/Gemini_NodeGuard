package exporter

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	"gemini-sub-checker/internal/types"
)

// ExportOptions holds options for exporting nodes.
type ExportOptions struct {
	RemarkPrefix   string
	IncludeLatency bool
}

// formatRemark returns the formatted remark for a node.
func formatRemark(node *types.ProxyNode, opts ExportOptions) string {
	originalRemark := node.DisplayName()

	prefix := opts.RemarkPrefix
	if prefix == "" {
		prefix = "[Gemini]"
	}

	if opts.IncludeLatency && node.Latency > 0 {
		return fmt.Sprintf("%s[%dms] %s", prefix, node.Latency.Milliseconds(), originalRemark)
	}
	return fmt.Sprintf("%s %s", prefix, originalRemark)
}

// ReconstructVMess reconstructs a vmess:// URI.
func ReconstructVMess(node *types.ProxyNode, remark string) (string, error) {
	// Follow the standard VMess JSON schema
	v := map[string]interface{}{
		"v":    "2",
		"ps":   remark,
		"add":  node.Server,
		"port": node.Port,
		"id":   node.UUID,
		"aid":  node.AlterID,
		"scy":  node.Cipher,
		"net":  string(node.Transport.Type),
		"type": "none",
		"host": node.Transport.Host,
		"path": node.Transport.Path,
		"tls":  "none",
		"sni":  node.TLS.ServerName,
		"alpn": strings.Join(node.TLS.ALPN, ","),
		"fp":   node.TLS.Fingerprint,
	}

	if v["scy"] == "" {
		v["scy"] = "auto"
	}

	if node.TLS.Enabled || node.Security == types.SecurityTLS {
		v["tls"] = "tls"
	} else if node.Security == types.SecurityReality {
		v["tls"] = "reality"
	}

	jsonData, err := json.Marshal(v)
	if err != nil {
		return "", err
	}

	encoded := base64.StdEncoding.EncodeToString(jsonData)
	return "vmess://" + encoded, nil
}

// ReconstructStandard reconstructs a standard URL format URI (like vless:// or trojan://).
func ReconstructStandard(node *types.ProxyNode, remark string) string {
	u := &url.URL{
		Scheme: string(node.Protocol),
		User:   url.User(node.UUID),
		Host:   fmt.Sprintf("%s:%d", node.Server, node.Port),
	}

	q := u.Query()

	if node.Security != "" && node.Security != types.SecurityNone {
		q.Set("security", string(node.Security))
	}
	if node.TLS.ServerName != "" {
		q.Set("sni", node.TLS.ServerName)
	}
	if len(node.TLS.ALPN) > 0 {
		q.Set("alpn", strings.Join(node.TLS.ALPN, ","))
	}
	if node.TLS.Fingerprint != "" {
		q.Set("fp", node.TLS.Fingerprint)
	}

	if node.Reality.Enabled {
		if node.Reality.PublicKey != "" {
			q.Set("pbk", node.Reality.PublicKey)
		}
		if node.Reality.ShortID != "" {
			q.Set("sid", node.Reality.ShortID)
		}
		if node.Reality.SpiderX != "" {
			q.Set("spx", node.Reality.SpiderX)
		}
	}

	if node.Transport.Type != "" {
		q.Set("type", string(node.Transport.Type))
	}
	if node.Transport.Host != "" {
		q.Set("host", node.Transport.Host)
	}
	if node.Transport.Path != "" {
		q.Set("path", node.Transport.Path)
	}
	if node.Transport.ServiceName != "" {
		q.Set("serviceName", node.Transport.ServiceName)
	}

	if node.Flow != "" {
		q.Set("flow", node.Flow)
	}

	// Set encoded query
	u.RawQuery = q.Encode()

	// Format returns a valid string without the fragment first
	// We append the fragment manually because url.URL.String() uses PathEscape,
	// which leaves '+' as '+' and encodes spaces as '%20', but our parser expects
	// QueryUnescape semantics where '+' becomes ' ' unless encoded as '%2B'.
	baseStr := u.String()

	if remark != "" {
		return baseStr + "#" + url.QueryEscape(remark)
	}

	return baseStr
}

// ReconstructURI reconstructs a raw URI based on the node and options.
func ReconstructURI(node *types.ProxyNode, opts ExportOptions) (string, error) {
	remark := formatRemark(node, opts)

	switch node.Protocol {
	case types.ProtocolVmess:
		return ReconstructVMess(node, remark)
	case types.ProtocolVless, types.ProtocolTrojan:
		return ReconstructStandard(node, remark), nil
	default:
		// For unsupported protocols, just fallback to raw if we must, or standard
		// For simplicity, fallback to ReconstructStandard for now
		return ReconstructStandard(node, remark), nil
	}
}

// ExportPlain returns raw URIs separated by \n.
func ExportPlain(nodes []*types.ProxyNode, opts ExportOptions) string {
	var lines []string
	for _, node := range nodes {
		uri, err := ReconstructURI(node, opts)
		if err == nil {
			lines = append(lines, uri)
		}
	}
	return strings.Join(lines, "\n")
}

// ExportBase64 combines all reconstructed URIs separated by \n and encodes into standard Base64.
func ExportBase64(nodes []*types.ProxyNode, opts ExportOptions) (string, error) {
	plain := ExportPlain(nodes, opts)
	if plain == "" {
		return "", nil
	}
	return base64.StdEncoding.EncodeToString([]byte(plain)), nil
}

// WriteToFile writes the output to disk, automatically creating any missing parent directories.
func WriteToFile(filePath string, content string) error {
	dir := filepath.Dir(filePath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return fmt.Errorf("failed to create directory: %w", err)
	}
	if err := os.WriteFile(filePath, []byte(content), 0644); err != nil {
		return fmt.Errorf("failed to write file: %w", err)
	}
	return nil
}

package parser

import (
	"errors"
	"fmt"
	"strings"

	"gemini-nodeguard/internal/types"
)

// ParseURI detects the scheme prefix and routes to the appropriate protocol parser.
func ParseURI(rawURI string) (*types.ProxyNode, error) {
	rawURI = strings.TrimSpace(rawURI)
	if rawURI == "" {
		return nil, errors.New("empty uri")
	}

	if strings.HasPrefix(rawURI, "vless://") {
		return parseVLESS(rawURI)
	}
	if strings.HasPrefix(rawURI, "vmess://") {
		return parseVMess(rawURI)
	}
	if strings.HasPrefix(rawURI, "trojan://") {
		return parseTrojan(rawURI)
	}

	return nil, fmt.Errorf("unsupported protocol scheme for URI: %s", rawURI)
}

// ParseAll parses a slice of raw URI strings, calls node.Validate(), and deduplicates
// nodes using node.DedupKey(), preserving first-seen order. Returns valid nodes and any encountered parse errors.
func ParseAll(lines []string) ([]*types.ProxyNode, []error) {
	var validNodes []*types.ProxyNode
	var parseErrors []error
	seen := make(map[string]bool)

	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}

		node, err := ParseURI(line)
		if err != nil {
			parseErrors = append(parseErrors, err)
			continue
		}

		if err := node.Validate(); err != nil {
			parseErrors = append(parseErrors, fmt.Errorf("validation failed for %s: %w", node.RawURI, err))
			continue
		}

		dedupKey := node.DedupKey()
		if !seen[dedupKey] {
			seen[dedupKey] = true
			validNodes = append(validNodes, node)
		}
	}

	return validNodes, parseErrors
}

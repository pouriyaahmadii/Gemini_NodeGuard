package fetcher

import (
	"bytes"
	"encoding/base64"
	"strings"
)

var knownSchemes = []string{
	"vless://",
	"vmess://",
	"trojan://",
	"ss://",
	"hysteria2://",
}

// DecodeSubscription sanitizes and decodes the given subscription payload.
// It handles plain text and various forms of Base64 encodings.
func DecodeSubscription(data []byte) ([]string, error) {
	// Pre-sanitization: strip BOM
	data = bytes.TrimPrefix(data, []byte("\xef\xbb\xbf"))

	// Convert to string and trim leading/trailing whitespace
	text := strings.TrimSpace(string(data))
	// Remove carriage returns to simplify line splitting
	text = strings.ReplaceAll(text, "\r", "")

	if text == "" {
		return []string{}, nil
	}

	// Check if it's already plaintext (contains known proxy schemes)
	isPlaintext := false
	for _, scheme := range knownSchemes {
		if strings.Contains(text, scheme) {
			isPlaintext = true
			break
		}
	}

	decodedText := text

	if !isPlaintext {
		// Strip all whitespace and newlines for Base64 decoding
		b64Text := strings.ReplaceAll(text, " ", "")
		b64Text = strings.ReplaceAll(b64Text, "\n", "")
		b64Text = strings.ReplaceAll(b64Text, "\t", "")

		decoded, ok := tryBase64Decode(b64Text)
		if ok {
			decodedText = decoded
			// Also strip carriage returns from decoded text just in case
			decodedText = strings.ReplaceAll(decodedText, "\r", "")
		}
	}

	return normalizeLines(decodedText), nil
}

// tryBase64Decode attempts multiple Base64 decoding variants.
func tryBase64Decode(data string) (string, bool) {
	decoders := []*base64.Encoding{
		base64.StdEncoding,
		base64.RawStdEncoding,
		base64.URLEncoding,
		base64.RawURLEncoding,
	}

	for _, dec := range decoders {
		decodedBytes, err := dec.DecodeString(data)
		if err == nil {
			return string(decodedBytes), true
		}
	}

	return "", false
}

// normalizeLines splits the text into non-empty, non-comment lines.
func normalizeLines(text string) []string {
	var results []string
	lines := strings.Split(text, "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if strings.HasPrefix(line, "#") || strings.HasPrefix(line, "//") {
			continue
		}
		results = append(results, line)
	}
	return results
}

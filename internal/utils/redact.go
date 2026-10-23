package utils

import (
	"net/url"
	"strings"
)

// RedactURL takes a full URL and returns just scheme://host:port.
func RedactURL(raw string) string {
	u, err := url.Parse(raw)
	if err != nil {
		// If it doesn't parse, try to extract scheme://host:port manually
		parts := strings.SplitN(raw, "://", 2)
		if len(parts) == 2 {
			rest := parts[1]
			slashIdx := strings.Index(rest, "/")
			if slashIdx != -1 {
				rest = rest[:slashIdx]
			}
			return parts[0] + "://" + rest
		}
		return "<redacted_invalid_url>"
	}

	host := u.Host
	if host == "" {
		host = u.Hostname()
	}

	if u.Scheme == "" {
		return host
	}
	return u.Scheme + "://" + host
}

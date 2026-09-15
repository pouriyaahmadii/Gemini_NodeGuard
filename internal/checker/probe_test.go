package checker

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"gemini-sub-checker/internal/types"
)

func TestProbe(t *testing.T) {
	tests := []struct {
		name       string
		statusCode int
		body       string
		delay      time.Duration
		wantPass   bool
		wantAlive  bool
	}{
		{
			name:       "Simulated 200 OK without geoblock",
			statusCode: http.StatusOK,
			body:       "<html><body>Welcome to Google</body></html>",
			wantPass:   true,
			wantAlive:  true,
		},
		{
			name:       "Simulated 200 OK with geoblock (isn't currently supported in your country)",
			statusCode: http.StatusOK,
			body:       "<html><body>Gemini isn't currently supported in your country</body></html>",
			wantPass:   false,
			wantAlive:  true,
		},
		{
			name:       "Simulated 200 OK with geoblock (not yet available in your region) - case insensitive",
			statusCode: http.StatusOK,
			body:       "<html><body>Jules is NOT YET AVAILABLE IN YOUR REGION.</body></html>",
			wantPass:   false,
			wantAlive:  true,
		},
		{
			name:       "Simulated 302 Found",
			statusCode: http.StatusFound,
			wantPass:   true,
			wantAlive:  true,
		},
		{
			name:       "Simulated 403 Forbidden",
			statusCode: http.StatusForbidden,
			wantPass:   false,
			wantAlive:  true,
		},
		{
			name:       "Simulated 500 Server Error",
			statusCode: http.StatusInternalServerError,
			wantPass:   false,
			wantAlive:  true,
		},
		{
			name:       "Timeout / Slow Server Response",
			statusCode: http.StatusOK,
			delay:      200 * time.Millisecond,
			wantPass:   false, // We will use a smaller context timeout for this test
			wantAlive:  false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Header.Get("User-Agent") == "" {
					t.Errorf("expected User-Agent header to be set")
				}
				if r.Header.Get("Accept") == "" {
					t.Errorf("expected Accept header to be set")
				}
				if tt.delay > 0 {
					time.Sleep(tt.delay)
				}
				w.WriteHeader(tt.statusCode)
				if tt.body != "" {
					w.Write([]byte(tt.body))
				}
			}))
			defer server.Close()

			// Create a mock dialer that does not follow redirects for testing 302
			client := server.Client()
			client.CheckRedirect = func(req *http.Request, via []*http.Request) error {
				return http.ErrUseLastResponse
			}

			node := &types.ProxyNode{}

			ctx := context.Background()
			var cancel context.CancelFunc
			if tt.delay > 0 {
				ctx, cancel = context.WithTimeout(ctx, 50*time.Millisecond)
				defer cancel()
			}

			Probe(ctx, client, node, server.URL)

			if node.IsGeminiCompatible != tt.wantPass {
				t.Errorf("Probe() IsGeminiCompatible = %v, want %v", node.IsGeminiCompatible, tt.wantPass)
			}

			if node.IsAlive != tt.wantAlive {
				t.Errorf("Probe() IsAlive = %v, want %v", node.IsAlive, tt.wantAlive)
			}

			if node.Latency <= 0 {
				t.Errorf("Expected Latency to be > 0, got %v", node.Latency)
			}
		})
	}
}

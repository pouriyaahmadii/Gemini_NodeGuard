package checker

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"gemini-nodeguard/internal/registry"
	"gemini-nodeguard/internal/types"
)

func TestProbe(t *testing.T) {
	svcReg := registry.GetServices()

	tests := []struct {
		name       string
		serviceID  string
		statusCode int
		body       string
		wantClass  types.Classification
	}{
		{
			name:       "Search - Simulated 200 OK",
			serviceID:  "search",
			statusCode: http.StatusOK,
			wantClass:  types.ClassificationConfirmedCompatible,
		},
		{
			name:       "Gemini - Simulated 200 OK without geoblock",
			serviceID:  "gemini",
			statusCode: http.StatusOK,
			body:       "<html><body>Welcome to Google Gemini</body></html>",
			wantClass:  types.ClassificationConfirmedCompatible,
		},
		{
			name:       "Gemini - Simulated 403 Forbidden without geoblock",
			serviceID:  "gemini",
			statusCode: http.StatusForbidden,
			body:       "<html><body>Generic Access Denied</body></html>",
			wantClass:  types.ClassificationAccessDenied,
		},
		{
			name:       "Gemini - Simulated 403 with geoblock (isn't currently supported in your country)",
			serviceID:  "gemini",
			statusCode: http.StatusForbidden,
			body:       "<html><body>Gemini isn't currently supported in your country</body></html>",
			wantClass:  types.ClassificationLikelyRegionRestricted,
		},
		{
			name:       "AI API - Simulated 403 Permission Denied (API Success)",
			serviceID:  "ai_api",
			statusCode: http.StatusForbidden,
			body:       `{"error": {"message": "permission_denied"}}`,
			wantClass:  types.ClassificationConfirmedCompatible,
		},
		{
			name:       "NotebookLM - Rate Limited",
			serviceID:  "notebooklm",
			statusCode: http.StatusTooManyRequests,
			wantClass:  types.ClassificationRateLimited,
		},
		{
			name:       "Jules - 500 Server Error",
			serviceID:  "jules",
			statusCode: http.StatusInternalServerError,
			wantClass:  types.ClassificationServiceError,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tt.statusCode)
				if tt.body != "" {
					w.Write([]byte(tt.body))
				}
			}))
			defer server.Close()

			client := server.Client()
			client.CheckRedirect = func(req *http.Request, via []*http.Request) error {
				return http.ErrUseLastResponse
			}

			node := types.NewProxyNode()
			ctx := context.Background()

			// Update the endpoint of the service definition to our local test server URL
			def := svcReg[tt.serviceID]
			testDef := &registry.ServiceDefinition{
				ID:           def.ID,
				Name:         def.Name,
				Endpoint:     server.URL,
				Method:       def.Method,
				RequiresAuth: def.RequiresAuth,
				StatusRules:  def.StatusRules,
			}

			result := Probe(ctx, client, node, testDef)

			if result.Classification != tt.wantClass {
				t.Errorf("Probe() Classification = %v, want %v (Status: %d, Body: %s)", result.Classification, tt.wantClass, result.HTTPStatus, tt.body)
			}
		})
	}
}

func TestProbeTransportError(t *testing.T) {
	node := types.NewProxyNode()
	ctx, cancel := context.WithTimeout(context.Background(), 1*time.Millisecond)
	defer cancel()

	// Use an unroutable address that will timeout/fail instantly in our tight context
	testDef := &registry.ServiceDefinition{
		ID:          "timeout_test",
		Endpoint:    "http://192.0.2.1", // TEST-NET-1 (unroutable)
		Method:      "GET",
		StatusRules: []registry.StatusRule{},
	}

	// Allow enough time for context to expire so HTTP transport immediately fails
	time.Sleep(5 * time.Millisecond)

	client := &http.Client{}
	result := Probe(ctx, client, node, testDef)

	if result.Classification != types.ClassificationTransportError {
		t.Errorf("Expected TransportError, got %v", result.Classification)
	}
}

package registry

import (
	"os"
	"testing"

	"gemini-nodeguard/internal/types"
)

func TestEvaluateResponse(t *testing.T) {
	gemini := GetServices()["gemini"]

	// 200 OK
	res := gemini.EvaluateResponse(200, "https://gemini.google.com", []byte("Welcome"))
	if res != types.ClassificationConfirmedCompatible {
		t.Errorf("Expected confirmed_compatible, got %s", res)
	}

	// 200 OK + geoblock body
	res = gemini.EvaluateResponse(200, "https://gemini.google.com", []byte("sorry, isn't currently supported in your country"))
	if res != types.ClassificationLikelyRegionRestricted {
		t.Errorf("Expected likely_region_restricted, got %s", res)
	}

	// 302 to accounts.google.com
	res = gemini.EvaluateResponse(302, "https://accounts.google.com/ServiceLogin", []byte(""))
	if res != types.ClassificationAuthenticationRequired {
		t.Errorf("Expected authentication_required, got %s", res)
	}

	// 302 to google.com/sorry
	res = gemini.EvaluateResponse(302, "https://google.com/sorry/index", []byte(""))
	if res != types.ClassificationLikelyRegionRestricted {
		t.Errorf("Expected likely_region_restricted, got %s", res)
	}

	// 403 Access Denied
	res = gemini.EvaluateResponse(403, "https://gemini.google.com", []byte("Forbidden"))
	if res != types.ClassificationAccessDenied {
		t.Errorf("Expected access_denied, got %s", res)
	}

	// 429
	res = gemini.EvaluateResponse(429, "https://gemini.google.com", []byte(""))
	if res != types.ClassificationRateLimited {
		t.Errorf("Expected rate_limited, got %s", res)
	}

	// 500
	res = gemini.EvaluateResponse(500, "https://gemini.google.com", []byte(""))
	if res != types.ClassificationServiceError {
		t.Errorf("Expected service_error, got %s", res)
	}
}

func TestLoadCustomRegistry(t *testing.T) {
	jsonStr := `{
		"version": 1,
		"services": [
			{
				"id": "test_service",
				"name": "Test Service",
				"endpoint": "https://test.com",
				"method": "POST",
				"requires_auth": true,
				"status_rules": [
					{
						"status": [201],
						"classification": "confirmed_compatible"
					}
				]
			}
		]
	}`

	f, _ := os.CreateTemp("", "registry-*.json")
	f.WriteString(jsonStr)
	f.Close()
	defer os.Remove(f.Name())

	err := LoadCustomRegistry(f.Name())
	if err != nil {
		t.Fatalf("Failed to load custom registry: %v", err)
	}

	svc := GetServices()["test_service"]
	if svc == nil {
		t.Fatalf("test_service not loaded")
	}

	if svc.Name != "Test Service" {
		t.Errorf("Expected Test Service, got %s", svc.Name)
	}
}

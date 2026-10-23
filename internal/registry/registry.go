package registry

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"strings"

	"gemini-nodeguard/internal/types"
)

type RegistryFile struct {
	Version  int                 `json:"version"`
	Services []ServiceDefinition `json:"services"`
}

type ServiceDefinition struct {
	ID              string            `json:"id"`
	Name            string            `json:"name"`
	Endpoint        string            `json:"endpoint"`
	Method          string            `json:"method,omitempty"`
	RequiresAuth    bool              `json:"requires_auth,omitempty"`
	FollowRedirects *bool             `json:"follow_redirects,omitempty"` // default true
	Headers         map[string]string `json:"headers,omitempty"`
	Body            string            `json:"body,omitempty"`
	StatusRules     []StatusRule      `json:"status_rules"`
}

func (s *ServiceDefinition) GetFollowRedirects() bool {
	if s.FollowRedirects == nil {
		return true
	}
	return *s.FollowRedirects
}

func (s *ServiceDefinition) GetMethod() string {
	if s.Method == "" {
		return "GET"
	}
	return s.Method
}

type StatusRule struct {
	Status            []int                `json:"status"`
	Classification    types.Classification `json:"classification"`
	URLContainsAny    []string             `json:"url_contains_any,omitempty"`
	URLNotContainsAny []string             `json:"url_not_contains_any,omitempty"`
	BodyContainsAny   []string             `json:"body_contains_any,omitempty"`
}

func (s *ServiceDefinition) EvaluateResponse(statusCode int, urlStr string, body []byte) types.Classification {
	lowerBody := bytes.ToLower(body)
	lowerURL := strings.ToLower(urlStr)

	for _, rule := range s.StatusRules {
		// Check status
		statusMatch := false
		for _, st := range rule.Status {
			if st == statusCode {
				statusMatch = true
				break
			}
		}
		if !statusMatch {
			continue
		}

		// Check URL Contains Any
		if len(rule.URLContainsAny) > 0 {
			match := false
			for _, kw := range rule.URLContainsAny {
				if strings.Contains(lowerURL, strings.ToLower(kw)) {
					match = true
					break
				}
			}
			if !match {
				continue
			}
		}

		// Check URL Not Contains Any
		if len(rule.URLNotContainsAny) > 0 {
			match := false
			for _, kw := range rule.URLNotContainsAny {
				if strings.Contains(lowerURL, strings.ToLower(kw)) {
					match = true
					break
				}
			}
			if match {
				continue
			}
		}

		// Check Body Contains Any
		if len(rule.BodyContainsAny) > 0 {
			match := false
			for _, kw := range rule.BodyContainsAny {
				if bytes.Contains(lowerBody, bytes.ToLower([]byte(kw))) {
					match = true
					break
				}
			}
			if !match {
				continue
			}
		}

		return rule.Classification
	}

	return types.ClassificationInconclusive
}

var defaultGeoblockKeywords = []string{
	"isn't currently supported in your country",
	"is not currently supported in your country",
	"not currently supported in your country",
	"not yet available in your region",
	"is not yet available in your region",
	"is not available in your region",
	"is not available in your country",
	"not available in your country",
	"unsupported country",
	"unsupported region",
	"not available in your location",
	"not supported in your country",
	"user location is not supported",
	"failed_precondition",
	"country or region",
	"not supported in your region",
	"geographic restriction",
}

// ... I'll write more default definitions ...

func boolPtr(b bool) *bool {
	return &b
}

var defaultServices = map[string]*ServiceDefinition{
	"search": {
		ID:           "search",
		Name:         "Google Search",
		Endpoint:     "https://www.google.com/generate_204",
		Method:       "GET",
		RequiresAuth: false,
		StatusRules: []StatusRule{
			{Status: []int{200, 204}, Classification: types.ClassificationConfirmedCompatible},
			{Status: []int{429}, Classification: types.ClassificationRateLimited},
			{Status: []int{500, 502, 503, 504}, Classification: types.ClassificationServiceError},
		},
	},
	"gemini": {
		ID:           "gemini",
		Name:         "Google Gemini",
		Endpoint:     "https://gemini.google.com",
		Method:       "GET",
		RequiresAuth: true,
		StatusRules: []StatusRule{
			{Status: []int{200, 204}, Classification: types.ClassificationLikelyRegionRestricted, BodyContainsAny: defaultGeoblockKeywords}, // Check body first if 200
			{Status: []int{200, 204}, Classification: types.ClassificationConfirmedCompatible},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationConfirmedCompatible, URLContainsAny: []string{"gemini.google.com"}, URLNotContainsAny: []string{"accounts.google.com", "sorry", "unsupported"}},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationAuthenticationRequired, URLContainsAny: []string{"accounts.google.com"}},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationLikelyRegionRestricted, URLContainsAny: []string{"sorry", "unsupported"}}, // sorry page or unsupported page
			{Status: []int{403}, Classification: types.ClassificationLikelyRegionRestricted, BodyContainsAny: defaultGeoblockKeywords},
			{Status: []int{403}, Classification: types.ClassificationAccessDenied},
			{Status: []int{429}, Classification: types.ClassificationRateLimited},
			{Status: []int{500, 502, 503, 504}, Classification: types.ClassificationServiceError},
		},
	},
	"aistudio": {
		ID:           "aistudio",
		Name:         "Google AI Studio",
		Endpoint:     "https://aistudio.google.com",
		Method:       "GET",
		RequiresAuth: true,
		StatusRules: []StatusRule{
			{Status: []int{200, 204}, Classification: types.ClassificationLikelyRegionRestricted, BodyContainsAny: defaultGeoblockKeywords},
			{Status: []int{200, 204}, Classification: types.ClassificationConfirmedCompatible},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationConfirmedCompatible, URLContainsAny: []string{"aistudio.google.com"}, URLNotContainsAny: []string{"accounts.google.com", "sorry", "unsupported"}},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationAuthenticationRequired, URLContainsAny: []string{"accounts.google.com"}},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationLikelyRegionRestricted, URLContainsAny: []string{"sorry", "unsupported"}},
			{Status: []int{403}, Classification: types.ClassificationLikelyRegionRestricted, BodyContainsAny: defaultGeoblockKeywords},
			{Status: []int{403}, Classification: types.ClassificationAccessDenied},
			{Status: []int{429}, Classification: types.ClassificationRateLimited},
			{Status: []int{500, 502, 503, 504}, Classification: types.ClassificationServiceError},
		},
	},
	"notebooklm": {
		ID:           "notebooklm",
		Name:         "Google NotebookLM",
		Endpoint:     "https://notebooklm.google.com",
		Method:       "GET",
		RequiresAuth: true,
		StatusRules: []StatusRule{
			{Status: []int{200, 204}, Classification: types.ClassificationLikelyRegionRestricted, BodyContainsAny: defaultGeoblockKeywords},
			{Status: []int{200, 204}, Classification: types.ClassificationConfirmedCompatible},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationConfirmedCompatible, URLContainsAny: []string{"notebooklm.google.com"}, URLNotContainsAny: []string{"accounts.google.com", "sorry", "unsupported"}},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationAuthenticationRequired, URLContainsAny: []string{"accounts.google.com"}},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationLikelyRegionRestricted, URLContainsAny: []string{"sorry", "unsupported"}},
			{Status: []int{403}, Classification: types.ClassificationLikelyRegionRestricted, BodyContainsAny: defaultGeoblockKeywords},
			{Status: []int{403}, Classification: types.ClassificationAccessDenied},
			{Status: []int{429}, Classification: types.ClassificationRateLimited},
			{Status: []int{500, 502, 503, 504}, Classification: types.ClassificationServiceError},
		},
	},
	"jules": {
		ID:           "jules",
		Name:         "Google Jules",
		Endpoint:     "https://jules.google.com",
		Method:       "GET",
		RequiresAuth: true,
		StatusRules: []StatusRule{
			{Status: []int{200, 204}, Classification: types.ClassificationLikelyRegionRestricted, BodyContainsAny: defaultGeoblockKeywords},
			{Status: []int{200, 204}, Classification: types.ClassificationConfirmedCompatible},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationConfirmedCompatible, URLContainsAny: []string{"jules.google.com"}, URLNotContainsAny: []string{"accounts.google.com", "sorry", "unsupported"}},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationAuthenticationRequired, URLContainsAny: []string{"accounts.google.com"}},
			{Status: []int{301, 302, 303, 307, 308}, Classification: types.ClassificationLikelyRegionRestricted, URLContainsAny: []string{"sorry", "unsupported"}},
			{Status: []int{403}, Classification: types.ClassificationLikelyRegionRestricted, BodyContainsAny: defaultGeoblockKeywords},
			{Status: []int{403}, Classification: types.ClassificationAccessDenied},
			{Status: []int{429}, Classification: types.ClassificationRateLimited},
			{Status: []int{500, 502, 503, 504}, Classification: types.ClassificationServiceError},
		},
	},
	"ai_api": {
		ID:           "ai_api",
		Name:         "Google AI API",
		Endpoint:     "https://generativelanguage.googleapis.com/v1beta/models",
		Method:       "GET",
		RequiresAuth: true,
		StatusRules: []StatusRule{
			{Status: []int{200, 204}, Classification: types.ClassificationLikelyRegionRestricted, BodyContainsAny: defaultGeoblockKeywords},
			{Status: []int{200, 204}, Classification: types.ClassificationConfirmedCompatible},
			{Status: []int{400}, Classification: types.ClassificationConfirmedCompatible, BodyContainsAny: []string{"invalid_argument", "api key not valid", "api key"}},
			{Status: []int{403}, Classification: types.ClassificationLikelyRegionRestricted, BodyContainsAny: defaultGeoblockKeywords},
			{Status: []int{403}, Classification: types.ClassificationConfirmedCompatible, BodyContainsAny: []string{"permission_denied", "unregistered", "method doesn't allow"}},
			{Status: []int{403}, Classification: types.ClassificationAccessDenied},
			{Status: []int{429}, Classification: types.ClassificationRateLimited},
			{Status: []int{500, 502, 503, 504}, Classification: types.ClassificationServiceError},
		},
	},
}

var activeRegistry map[string]*ServiceDefinition

func init() {
	activeRegistry = make(map[string]*ServiceDefinition)
	for k, v := range defaultServices {
		activeRegistry[k] = v
	}
}

// GetServices returns the current registered services.
func GetServices() map[string]*ServiceDefinition {
	return activeRegistry
}

// LoadCustomRegistry loads a custom JSON registry file.
func LoadCustomRegistry(path string) error {
	f, err := os.Open(path)
	if err != nil {
		return fmt.Errorf("failed to open registry file: %w", err)
	}
	defer f.Close()

	stat, err := f.Stat()
	if err != nil {
		return err
	}
	if stat.Size() > 1024*1024 {
		return fmt.Errorf("registry file too large (max 1MB)")
	}

	var file RegistryFile
	if err := json.NewDecoder(f).Decode(&file); err != nil {
		return fmt.Errorf("failed to parse registry json: %w", err)
	}

	if file.Version != 1 {
		return fmt.Errorf("unsupported registry version: %d (expected 1)", file.Version)
	}

	if len(file.Services) > 32 {
		return fmt.Errorf("too many services (max 32)")
	}

	for i, s := range file.Services {
		if err := validateService(&s); err != nil {
			return fmt.Errorf("invalid service at index %d (%s): %w", i, s.ID, err)
		}
	}

	// Apply definitions
	for i := range file.Services {
		s := file.Services[i] // copy
		activeRegistry[s.ID] = &s
	}

	return nil
}

func validateService(s *ServiceDefinition) error {
	if s.ID == "" {
		return fmt.Errorf("missing id")
	}
	if len(s.ID) > 32 {
		return fmt.Errorf("id too long")
	}
	for _, c := range s.ID {
		if !((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
			return fmt.Errorf("invalid id format (use ^[a-z0-9_]+$)")
		}
	}
	if s.Name == "" {
		return fmt.Errorf("missing name")
	}
	if s.Endpoint == "" {
		return fmt.Errorf("missing endpoint")
	}
	if !strings.HasPrefix(s.Endpoint, "http://") && !strings.HasPrefix(s.Endpoint, "https://") {
		return fmt.Errorf("endpoint must be http(s)")
	}

	validClassifications := map[types.Classification]bool{
		types.ClassificationConfirmedCompatible:    true,
		types.ClassificationLikelyRegionRestricted: true,
		types.ClassificationAuthenticationRequired: true,
		types.ClassificationAccessDenied:           true,
		types.ClassificationRateLimited:            true,
		types.ClassificationServiceError:           true,
		types.ClassificationTransportError:         true,
		types.ClassificationInconclusive:           true,
	}

	for j, rule := range s.StatusRules {
		if len(rule.Status) == 0 {
			return fmt.Errorf("rule %d missing status codes", j)
		}
		if !validClassifications[rule.Classification] {
			return fmt.Errorf("rule %d has unknown classification: %s", j, rule.Classification)
		}
	}
	return nil
}

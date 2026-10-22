package registry

import (
	"bytes"
	"net/http"
	"strings"

	"gemini-nodeguard/internal/types"
)

// ServiceDefinition represents a configurable service to test against.
type ServiceDefinition struct {
	ID               string
	Name             string
	Endpoint         string
	Method           string
	RequiresAuth     bool
	EvaluateResponse func(resp *http.Response, body []byte) types.Classification
}

var geoblockKeywords = [][]byte{
	[]byte("isn't currently supported in your country"),
	[]byte("is not currently supported in your country"),
	[]byte("not currently supported in your country"),
	[]byte("not yet available in your region"),
	[]byte("is not yet available in your region"),
	[]byte("is not available in your region"),
	[]byte("is not available in your country"),
	[]byte("not available in your country"),
	[]byte("unsupported country"),
	[]byte("unsupported region"),
	[]byte("not available in your location"),
	[]byte("not supported in your country"),
	[]byte("user location is not supported"),
	[]byte("failed_precondition"),
	[]byte("country or region"),
	[]byte("not supported in your region"),
	[]byte("geographic restriction"),
}

func containsGeoblock(body []byte) bool {
	lowerBody := bytes.ToLower(body)
	for _, kw := range geoblockKeywords {
		if bytes.Contains(lowerBody, kw) {
			return true
		}
	}
	return false
}

// GetServices returns the standard list of registered services.
func GetServices() map[string]*ServiceDefinition {
	return map[string]*ServiceDefinition{
		"search": {
			ID:           "search",
			Name:         "Google Search",
			Endpoint:     "https://www.google.com/generate_204",
			Method:       http.MethodGet,
			RequiresAuth: false,
			EvaluateResponse: func(resp *http.Response, body []byte) types.Classification {
				if resp.StatusCode == http.StatusNoContent || resp.StatusCode == http.StatusOK {
					return types.ClassificationConfirmedCompatible
				}
				if resp.StatusCode == http.StatusTooManyRequests {
					return types.ClassificationRateLimited
				}
				if resp.StatusCode >= 500 {
					return types.ClassificationServiceError
				}
				return types.ClassificationInconclusive
			},
		},
		"gemini": {
			ID:           "gemini",
			Name:         "Google Gemini",
			Endpoint:     "https://gemini.google.com",
			Method:       http.MethodGet,
			RequiresAuth: true,
			EvaluateResponse: func(resp *http.Response, body []byte) types.Classification {
				if resp.StatusCode == http.StatusTooManyRequests {
					return types.ClassificationRateLimited
				}
				if resp.StatusCode >= 500 {
					return types.ClassificationServiceError
				}
				if resp.StatusCode == http.StatusForbidden {
					if containsGeoblock(body) {
						return types.ClassificationLikelyRegionRestricted
					}
					return types.ClassificationAccessDenied
				}
				if resp.StatusCode == http.StatusOK || (resp.StatusCode >= 300 && resp.StatusCode < 400) {
					if containsGeoblock(body) {
						return types.ClassificationLikelyRegionRestricted
					}
					return types.ClassificationConfirmedCompatible
				}
				return types.ClassificationInconclusive
			},
		},
		"aistudio": {
			ID:           "aistudio",
			Name:         "Google AI Studio",
			Endpoint:     "https://aistudio.google.com",
			Method:       http.MethodGet,
			RequiresAuth: true,
			EvaluateResponse: func(resp *http.Response, body []byte) types.Classification {
				if resp.StatusCode == http.StatusTooManyRequests {
					return types.ClassificationRateLimited
				}
				if resp.StatusCode >= 500 {
					return types.ClassificationServiceError
				}
				if resp.StatusCode == http.StatusForbidden {
					if containsGeoblock(body) {
						return types.ClassificationLikelyRegionRestricted
					}
					return types.ClassificationAccessDenied
				}
				if resp.StatusCode == http.StatusOK || (resp.StatusCode >= 300 && resp.StatusCode < 400) {
					if containsGeoblock(body) {
						return types.ClassificationLikelyRegionRestricted
					}
					return types.ClassificationConfirmedCompatible
				}
				return types.ClassificationInconclusive
			},
		},
		"notebooklm": {
			ID:           "notebooklm",
			Name:         "Google NotebookLM",
			Endpoint:     "https://notebooklm.google.com",
			Method:       http.MethodGet,
			RequiresAuth: true,
			EvaluateResponse: func(resp *http.Response, body []byte) types.Classification {
				if resp.StatusCode == http.StatusTooManyRequests {
					return types.ClassificationRateLimited
				}
				if resp.StatusCode >= 500 {
					return types.ClassificationServiceError
				}
				if resp.StatusCode == http.StatusForbidden {
					if containsGeoblock(body) {
						return types.ClassificationLikelyRegionRestricted
					}
					return types.ClassificationAccessDenied
				}
				if resp.StatusCode == http.StatusOK || (resp.StatusCode >= 300 && resp.StatusCode < 400) {
					if containsGeoblock(body) {
						return types.ClassificationLikelyRegionRestricted
					}
					return types.ClassificationConfirmedCompatible
				}
				return types.ClassificationInconclusive
			},
		},
		"jules": {
			ID:           "jules",
			Name:         "Google Jules",
			Endpoint:     "https://jules.google.com",
			Method:       http.MethodGet,
			RequiresAuth: true,
			EvaluateResponse: func(resp *http.Response, body []byte) types.Classification {
				if resp.StatusCode == http.StatusTooManyRequests {
					return types.ClassificationRateLimited
				}
				if resp.StatusCode >= 500 {
					return types.ClassificationServiceError
				}
				if resp.StatusCode == http.StatusForbidden {
					if containsGeoblock(body) {
						return types.ClassificationLikelyRegionRestricted
					}
					return types.ClassificationAccessDenied
				}
				if resp.StatusCode == http.StatusOK || (resp.StatusCode >= 300 && resp.StatusCode < 400) {
					if containsGeoblock(body) {
						return types.ClassificationLikelyRegionRestricted
					}
					return types.ClassificationConfirmedCompatible
				}
				return types.ClassificationInconclusive
			},
		},
		"ai_api": {
			ID:           "ai_api",
			Name:         "Google AI API",
			Endpoint:     "https://generativelanguage.googleapis.com/v1beta/models",
			Method:       http.MethodGet,
			RequiresAuth: true,
			EvaluateResponse: func(resp *http.Response, body []byte) types.Classification {
				lowerBody := strings.ToLower(string(body))

				if resp.StatusCode == http.StatusTooManyRequests {
					return types.ClassificationRateLimited
				}
				if resp.StatusCode >= 500 {
					return types.ClassificationServiceError
				}

				if containsGeoblock(body) {
					return types.ClassificationLikelyRegionRestricted
				}

				if resp.StatusCode == http.StatusForbidden {
					if strings.Contains(lowerBody, "permission_denied") ||
						strings.Contains(lowerBody, "unregistered") ||
						strings.Contains(lowerBody, "method doesn't allow") {
						return types.ClassificationConfirmedCompatible
					}
					return types.ClassificationAccessDenied
				}

				if resp.StatusCode == http.StatusBadRequest {
					if strings.Contains(lowerBody, "invalid_argument") ||
						strings.Contains(lowerBody, "api key not valid") ||
						strings.Contains(lowerBody, "api key") {
						return types.ClassificationConfirmedCompatible
					}
				}

				if resp.StatusCode == http.StatusOK {
					return types.ClassificationConfirmedCompatible
				}

				return types.ClassificationInconclusive
			},
		},
	}
}

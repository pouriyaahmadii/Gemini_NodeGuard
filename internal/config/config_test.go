package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLoadConfig(t *testing.T) {
	// Create a temp config
	dir := t.TempDir()
	path := filepath.Join(dir, "config.json")

	err := os.WriteFile(path, []byte(`{
		"concurrency": 20,
		"services": ["gemini", "search"]
	}`), 0o644)
	if err != nil {
		t.Fatal(err)
	}

	// 1. File loading
	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatal(err)
	}
	if cfg.Concurrency != 20 {
		t.Errorf("Expected 20, got %d", cfg.Concurrency)
	}

	// 2. Env overlay
	os.Setenv("NG_SUB_URLS", "http://env.com")
	defer os.Unsetenv("NG_SUB_URLS")

	cfg2, err := LoadConfig(path)
	if err != nil {
		t.Fatal(err)
	}
	if len(cfg2.SubURLs) != 1 || cfg2.SubURLs[0] != "http://env.com" {
		t.Errorf("Expected env.com, got %v", cfg2.SubURLs)
	}

	// 3. Invalid format
	os.WriteFile(path, []byte(`{invalid`), 0o644)
	_, err = LoadConfig(path)
	if err == nil {
		t.Fatal("Expected JSON parse error")
	}

	// 4. Missing file
	cfg4, err := LoadConfig(filepath.Join(dir, "missing.json"))
	if err != nil {
		t.Fatal("Missing file should not return error, should return default config")
	}
	if cfg4.Concurrency != 10 {
		t.Errorf("Expected default 10, got %d", cfg4.Concurrency)
	}
}

package config

import (
	"encoding/json"
	"fmt"
	"os"
	"time"
)

// Config represents the application configuration.
type Config struct {
	SubURLs      []string      `json:"sub_urls"`
	TargetURLs   []string      `json:"target_urls"`
	OutputPath   string        `json:"output_path"`
	Concurrency  int           `json:"concurrency"`
	Timeout      time.Duration `json:"-"`
	TimeoutStr   string        `json:"timeout"`
	SingboxPath  string        `json:"singbox_path"`
	RemarkPrefix string        `json:"remark_prefix"`
}

// DefaultConfig returns a Config with sensible default values.
func DefaultConfig() *Config {
	return &Config{
		SubURLs:      []string{},
		TargetURLs:   []string{"https://gemini.google.com"},
		OutputPath:   "output/gemini.txt",
		Concurrency:  10,
		Timeout:      8 * time.Second,
		SingboxPath:  "sing-box",
		RemarkPrefix: "[Gemini]",
	}
}

// LoadConfig loads the configuration from a JSON file.
// If the file does not exist, it falls back to defaults.
func LoadConfig(path string) (*Config, error) {
	cfg := DefaultConfig()

	if path == "" {
		return cfg, nil
	}

	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return cfg, nil
		}
		return nil, fmt.Errorf("failed to read config file: %w", err)
	}

	if err := json.Unmarshal(data, cfg); err != nil {
		return nil, fmt.Errorf("failed to parse config file: %w", err)
	}

	// Parse TimeoutStr if provided
	if cfg.TimeoutStr != "" {
		parsedTimeout, err := time.ParseDuration(cfg.TimeoutStr)
		if err != nil {
			return nil, fmt.Errorf("invalid timeout duration format: %w", err)
		}
		cfg.Timeout = parsedTimeout
	}

	return cfg, nil
}

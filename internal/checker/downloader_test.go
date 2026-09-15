package checker

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"fmt"
	"os"
	"path/filepath"
	"testing"
)

func TestGenerateDownloadURL(t *testing.T) {
	tests := []struct {
		version     string
		goos        string
		goarch      string
		expectedURL string
		expectedZip bool
		expectedOk  bool
	}{
		{
			version:     "1.10.7",
			goos:        "linux",
			goarch:      "amd64",
			expectedURL: "https://github.com/SagerNet/sing-box/releases/download/v1.10.7/sing-box-1.10.7-linux-amd64.tar.gz",
			expectedZip: false,
			expectedOk:  true,
		},
		{
			version:     "1.10.7",
			goos:        "darwin",
			goarch:      "arm64",
			expectedURL: "https://github.com/SagerNet/sing-box/releases/download/v1.10.7/sing-box-1.10.7-darwin-arm64.tar.gz",
			expectedZip: false,
			expectedOk:  true,
		},
		{
			version:     "1.10.7",
			goos:        "windows",
			goarch:      "amd64",
			expectedURL: "https://github.com/SagerNet/sing-box/releases/download/v1.10.7/sing-box-1.10.7-windows-amd64.zip",
			expectedZip: true,
			expectedOk:  true,
		},
		{
			version:     "1.10.7",
			goos:        "unknown",
			goarch:      "amd64",
			expectedURL: "",
			expectedZip: false,
			expectedOk:  false,
		},
	}

	for _, tt := range tests {
		t.Run(fmt.Sprintf("%s-%s", tt.goos, tt.goarch), func(t *testing.T) {
			url, isZip := GenerateDownloadURL(tt.version, tt.goos, tt.goarch)
			if (url != "") != tt.expectedOk {
				t.Errorf("expected ok=%v, got %v", tt.expectedOk, url != "")
			}
			if url != tt.expectedURL {
				t.Errorf("expected url %q, got %q", tt.expectedURL, url)
			}
			if isZip != tt.expectedZip {
				t.Errorf("expected zip %v, got %v", tt.expectedZip, isZip)
			}
		})
	}
}

func TestExtractZip(t *testing.T) {
	// Create a temp directory for testing
	tempDir, err := os.MkdirTemp("", "extract-zip-test")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	zipPath := filepath.Join(tempDir, "test.zip")
	destPath := filepath.Join(tempDir, "sing-box.exe")

	// Create a dummy zip file
	f, err := os.Create(zipPath)
	if err != nil {
		t.Fatal(err)
	}
	w := zip.NewWriter(f)
	fw, err := w.Create("sing-box-1.10.7-windows-amd64/sing-box.exe")
	if err != nil {
		t.Fatal(err)
	}
	_, err = fw.Write([]byte("dummy content"))
	if err != nil {
		t.Fatal(err)
	}
	w.Close()
	f.Close()

	err = extractZip(zipPath, destPath, "sing-box.exe")
	if err != nil {
		t.Fatalf("extractZip failed: %v", err)
	}

	content, err := os.ReadFile(destPath)
	if err != nil {
		t.Fatalf("failed to read extracted file: %v", err)
	}

	if string(content) != "dummy content" {
		t.Errorf("expected 'dummy content', got '%s'", string(content))
	}
}

func TestExtractTarGz(t *testing.T) {
	// Create a temp directory for testing
	tempDir, err := os.MkdirTemp("", "extract-targz-test")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	tarGzPath := filepath.Join(tempDir, "test.tar.gz")
	destPath := filepath.Join(tempDir, "sing-box")

	// Create a dummy tar.gz file
	f, err := os.Create(tarGzPath)
	if err != nil {
		t.Fatal(err)
	}
	gw := gzip.NewWriter(f)
	tw := tar.NewWriter(gw)

	hdr := &tar.Header{
		Name: "sing-box-1.10.7-linux-amd64/sing-box",
		Mode: 0755,
		Size: int64(len("dummy content")),
	}
	if err := tw.WriteHeader(hdr); err != nil {
		t.Fatal(err)
	}
	if _, err := tw.Write([]byte("dummy content")); err != nil {
		t.Fatal(err)
	}
	tw.Close()
	gw.Close()
	f.Close()

	err = extractTarGz(tarGzPath, destPath, "sing-box")
	if err != nil {
		t.Fatalf("extractTarGz failed: %v", err)
	}

	content, err := os.ReadFile(destPath)
	if err != nil {
		t.Fatalf("failed to read extracted file: %v", err)
	}

	if string(content) != "dummy content" {
		t.Errorf("expected 'dummy content', got '%s'", string(content))
	}
}

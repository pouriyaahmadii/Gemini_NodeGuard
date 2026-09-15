package checker

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
)

const DefaultSingboxVersion = "1.10.7"

// ResolveSingbox resolves the path to the sing-box binary, downloading it if necessary.
func ResolveSingbox(customPath string) (string, error) {
	// 1. Custom flag path
	if customPath != "" {
		if _, err := os.Stat(customPath); err == nil {
			return customPath, nil
		}
		return "", fmt.Errorf("custom singbox path specified but not found: %s", customPath)
	}

	// 2. System $PATH
	binaryName := "sing-box"
	if runtime.GOOS == "windows" {
		binaryName = "sing-box.exe"
	}

	if path, err := exec.LookPath(binaryName); err == nil {
		return path, nil
	}

	// 3. Local cache directory
	homeDir, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("failed to get user home directory: %w", err)
	}

	cacheDir := filepath.Join(homeDir, ".gemini-sub-checker", "bin")
	cachedPath := filepath.Join(cacheDir, binaryName)

	if _, err := os.Stat(cachedPath); err == nil {
		return cachedPath, nil
	}

	// 4. Auto-download to the local cache
	log.Printf("sing-box not found. Downloading for %s/%s...", runtime.GOOS, runtime.GOARCH)

	if err := os.MkdirAll(cacheDir, 0755); err != nil {
		return "", fmt.Errorf("failed to create cache directory: %w", err)
	}

	if err := downloadSingbox(cachedPath, DefaultSingboxVersion, runtime.GOOS, runtime.GOARCH); err != nil {
		return "", fmt.Errorf("failed to download sing-box: %w", err)
	}

	log.Printf("sing-box downloaded successfully to %s", cachedPath)
	return cachedPath, nil
}

// GenerateDownloadURL constructs the GitHub release URL for sing-box.
func GenerateDownloadURL(version, goos, goarch string) (string, bool) {
	var archiveName string
	isZip := false

	switch goos {
	case "linux":
		archiveName = fmt.Sprintf("sing-box-%s-linux-%s.tar.gz", version, goarch)
	case "darwin":
		archiveName = fmt.Sprintf("sing-box-%s-darwin-%s.tar.gz", version, goarch)
	case "windows":
		archiveName = fmt.Sprintf("sing-box-%s-windows-%s.zip", version, goarch)
		isZip = true
	default:
		return "", false
	}

	url := fmt.Sprintf("https://github.com/SagerNet/sing-box/releases/download/v%s/%s", version, archiveName)
	return url, isZip
}

func downloadSingbox(destPath, version, goos, goarch string) error {
	downloadURL, isZip := GenerateDownloadURL(version, goos, goarch)
	if downloadURL == "" {
		return fmt.Errorf("unsupported OS/Arch combination: %s/%s", goos, goarch)
	}

	resp, err := http.Get(downloadURL)
	if err != nil {
		return fmt.Errorf("failed to download archive: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("failed to download archive, HTTP status: %d", resp.StatusCode)
	}

	// Create a temporary file to hold the archive
	tmpArchive, err := os.CreateTemp("", "sing-box-archive-*")
	if err != nil {
		return fmt.Errorf("failed to create temp archive file: %w", err)
	}
	defer os.Remove(tmpArchive.Name())
	defer tmpArchive.Close()

	if _, err := io.Copy(tmpArchive, resp.Body); err != nil {
		return fmt.Errorf("failed to save archive to temp file: %w", err)
	}
	tmpArchive.Close() // Close before extracting

	binaryName := "sing-box"
	if goos == "windows" {
		binaryName = "sing-box.exe"
	}

	// Extract binary from archive
	if isZip {
		err = extractZip(tmpArchive.Name(), destPath, binaryName)
	} else {
		err = extractTarGz(tmpArchive.Name(), destPath, binaryName)
	}

	if err != nil {
		return fmt.Errorf("failed to extract binary: %w", err)
	}

	// Ensure executable permissions on Unix-like systems
	if goos != "windows" {
		if err := os.Chmod(destPath, 0755); err != nil {
			return fmt.Errorf("failed to set executable permissions: %w", err)
		}
	}

	return nil
}

func extractZip(archivePath, destPath, targetFileName string) error {
	r, err := zip.OpenReader(archivePath)
	if err != nil {
		return err
	}
	defer r.Close()

	for _, f := range r.File {
		if filepath.Base(f.Name) == targetFileName {
			rc, err := f.Open()
			if err != nil {
				return err
			}
			defer rc.Close()

			outFile, err := os.OpenFile(destPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode())
			if err != nil {
				return err
			}
			defer outFile.Close()

			if _, err := io.Copy(outFile, rc); err != nil {
				return err
			}
			return nil
		}
	}

	return fmt.Errorf("%s not found in zip archive", targetFileName)
}

func extractTarGz(archivePath, destPath, targetFileName string) error {
	f, err := os.Open(archivePath)
	if err != nil {
		return err
	}
	defer f.Close()

	gzr, err := gzip.NewReader(f)
	if err != nil {
		return err
	}
	defer gzr.Close()

	tr := tar.NewReader(gzr)

	for {
		header, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}

		if header.Typeflag == tar.TypeReg && filepath.Base(header.Name) == targetFileName {
			outFile, err := os.OpenFile(destPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, os.FileMode(header.Mode))
			if err != nil {
				return err
			}
			defer outFile.Close()

			if _, err := io.Copy(outFile, tr); err != nil {
				return err
			}
			return nil
		}
	}

	return fmt.Errorf("%s not found in tar.gz archive", targetFileName)
}

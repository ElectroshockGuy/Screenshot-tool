package main

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
	_ "embed"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
)

//go:embed embed/app.jar
var jarData []byte

//go:embed embed/jre.tar.gz
var jreData []byte

const appName = "springboot-swing"

// Version is set at build time via ldflags
var Version = "dev"

func main() {
	// Handle --version flag
	if len(os.Args) > 1 && (os.Args[1] == "--version" || os.Args[1] == "-v") {
		fmt.Printf("%s version %s (%s/%s)\n", appName, Version, runtime.GOOS, runtime.GOARCH)
		os.Exit(0)
	}

	// Get user's home directory for cache
	homeDir, err := os.UserHomeDir()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error getting home directory: %v\n", err)
		os.Exit(1)
	}

	// Create app directory in user's home
	appDir := filepath.Join(homeDir, "."+appName)
	jreDir := filepath.Join(appDir, "jre")
	appExtractDir := filepath.Join(appDir, "app")
	nativeDir := filepath.Join(appDir, "native")

	// Check if we need to extract
	needExtract := false
	if _, err := os.Stat(jreDir); os.IsNotExist(err) {
		needExtract = true
	}
	if _, err := os.Stat(appExtractDir); os.IsNotExist(err) {
		needExtract = true
	}

	if needExtract {
		fmt.Println("First run, extracting runtime environment...")

		// Create app directory
		if err := os.MkdirAll(appDir, 0755); err != nil {
			fmt.Fprintf(os.Stderr, "Error creating app directory: %v\n", err)
			os.Exit(1)
		}

		// Extract JRE
		if err := extractTarGz(jreData, appDir); err != nil {
			fmt.Fprintf(os.Stderr, "Error extracting JRE: %v\n", err)
			os.Exit(1)
		}

		// Extract the Spring Boot JAR (to avoid nested JAR issues)
		if err := extractSpringBootJar(jarData, appExtractDir, nativeDir); err != nil {
			fmt.Fprintf(os.Stderr, "Error extracting application: %v\n", err)
			os.Exit(1)
		}

		fmt.Println("Extraction complete!")
	}

	// Find java executable
	javaExe := "java"
	if runtime.GOOS == "windows" {
		javaExe = "java.exe"
	}

	// Look for java in the extracted JRE
	var javaPath string
	err = filepath.Walk(jreDir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if info.Name() == javaExe && !info.IsDir() {
			javaPath = path
			return filepath.SkipAll
		}
		return nil
	})

	if javaPath == "" {
		// Try common paths
		possiblePaths := []string{
			filepath.Join(jreDir, "bin", javaExe),
			filepath.Join(jreDir, "Contents", "Home", "bin", javaExe),
		}
		entries, _ := os.ReadDir(jreDir)
		for _, entry := range entries {
			if entry.IsDir() {
				possiblePaths = append(possiblePaths,
					filepath.Join(jreDir, entry.Name(), "bin", javaExe),
					filepath.Join(jreDir, entry.Name(), "Contents", "Home", "bin", javaExe),
				)
			}
		}
		for _, p := range possiblePaths {
			if _, err := os.Stat(p); err == nil {
				javaPath = p
				break
			}
		}
	}

	if javaPath == "" {
		fmt.Fprintf(os.Stderr, "Error: Java executable not found in JRE\n")
		os.Exit(1)
	}

	// Make java executable (for Unix systems)
	if runtime.GOOS != "windows" {
		os.Chmod(javaPath, 0755)
	}

	// Build classpath from extracted JARs
	classPath := buildClasspath(appExtractDir)

	// Build args
	args := []string{
		"-Djnativehook.lib.path=" + nativeDir,
		"-Djava.library.path=" + nativeDir,
		"-cp", classPath,
		"org.springframework.boot.loader.JarLauncher",
	}
	args = append(args, os.Args[1:]...)

	cmd := exec.Command(javaPath, args...)
	cmd.Dir = appExtractDir // Set working directory
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = os.Stdin
	cmd.Env = os.Environ()

	if err := cmd.Run(); err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			os.Exit(exitErr.ExitCode())
		}
		fmt.Fprintf(os.Stderr, "Error running application: %v\n", err)
		os.Exit(1)
	}
}

// buildClasspath builds the classpath from extracted directories
func buildClasspath(appDir string) string {
	var paths []string

	// Add main classes
	paths = append(paths, appDir)

	// Add BOOT-INF/classes
	bootClasses := filepath.Join(appDir, "BOOT-INF", "classes")
	if _, err := os.Stat(bootClasses); err == nil {
		paths = append(paths, bootClasses)
	}

	// Add all JARs in BOOT-INF/lib
	bootLib := filepath.Join(appDir, "BOOT-INF", "lib")
	if entries, err := os.ReadDir(bootLib); err == nil {
		for _, entry := range entries {
			if strings.HasSuffix(entry.Name(), ".jar") {
				paths = append(paths, filepath.Join(bootLib, entry.Name()))
			}
		}
	}

	sep := ":"
	if runtime.GOOS == "windows" {
		sep = ";"
	}
	return strings.Join(paths, sep)
}

// extractSpringBootJar extracts the Spring Boot JAR and its native libraries
func extractSpringBootJar(data []byte, destDir, nativeDir string) error {
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return fmt.Errorf("failed to open JAR: %w", err)
	}

	// Create directories
	if err := os.MkdirAll(destDir, 0755); err != nil {
		return err
	}
	if err := os.MkdirAll(nativeDir, 0755); err != nil {
		return err
	}

	// Determine which native lib pattern we need
	var libPattern string
	switch runtime.GOOS {
	case "darwin":
		if runtime.GOARCH == "arm64" {
			libPattern = "darwin/arm64"
		} else {
			libPattern = "darwin/x86_64"
		}
	case "linux":
		switch runtime.GOARCH {
		case "arm64":
			libPattern = "linux/arm64"
		case "arm":
			libPattern = "linux/arm"
		case "386":
			libPattern = "linux/x86"
		default:
			libPattern = "linux/x86_64"
		}
	case "windows":
		switch runtime.GOARCH {
		case "arm":
			libPattern = "windows/arm"
		case "386":
			libPattern = "windows/x86"
		default:
			libPattern = "windows/x86_64"
		}
	}

	// Extract all files
	for _, f := range reader.File {
		targetPath := filepath.Join(destDir, f.Name)

		if f.FileInfo().IsDir() {
			os.MkdirAll(targetPath, 0755)
			continue
		}

		// Ensure parent directory exists
		os.MkdirAll(filepath.Dir(targetPath), 0755)

		// Extract file
		rc, err := f.Open()
		if err != nil {
			return err
		}

		outFile, err := os.OpenFile(targetPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, f.Mode())
		if err != nil {
			rc.Close()
			return err
		}

		_, err = io.Copy(outFile, rc)
		rc.Close()
		outFile.Close()
		if err != nil {
			return err
		}

		// If this is the jnativehook JAR, extract native libraries from it
		if strings.Contains(f.Name, "jnativehook") && strings.HasSuffix(f.Name, ".jar") {
			if err := extractNativeFromJar(targetPath, nativeDir, libPattern); err != nil {
				fmt.Printf("Warning: failed to extract native libs: %v\n", err)
			}
		}
	}

	return nil
}

// extractNativeFromJar extracts native libraries from a JAR file
func extractNativeFromJar(jarPath, nativeDir, libPattern string) error {
	jarReader, err := zip.OpenReader(jarPath)
	if err != nil {
		return err
	}
	defer jarReader.Close()

	for _, f := range jarReader.File {
		if strings.Contains(f.Name, libPattern) &&
			(strings.HasSuffix(f.Name, ".dylib") ||
				strings.HasSuffix(f.Name, ".so") ||
				strings.HasSuffix(f.Name, ".dll")) {

			rc, err := f.Open()
			if err != nil {
				return err
			}

			libName := filepath.Base(f.Name)
			destPath := filepath.Join(nativeDir, libName)

			outFile, err := os.OpenFile(destPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0755)
			if err != nil {
				rc.Close()
				return err
			}

			_, err = io.Copy(outFile, rc)
			rc.Close()
			outFile.Close()

			if err != nil {
				return err
			}

			fmt.Printf("Extracted native library: %s\n", libName)
		}
	}

	return nil
}

func extractTarGz(data []byte, destDir string) error {
	reader := bytes.NewReader(data)

	gzReader, err := gzip.NewReader(reader)
	if err != nil {
		return fmt.Errorf("failed to create gzip reader: %w", err)
	}
	defer gzReader.Close()

	tarReader := tar.NewReader(gzReader)

	jreDir := filepath.Join(destDir, "jre")
	if err := os.MkdirAll(jreDir, 0755); err != nil {
		return err
	}

	for {
		header, err := tarReader.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return fmt.Errorf("failed to read tar entry: %w", err)
		}

		cleanName := filepath.Clean(header.Name)
		parts := strings.SplitN(cleanName, string(filepath.Separator), 2)
		var targetPath string
		if len(parts) > 1 {
			targetPath = filepath.Join(jreDir, parts[1])
		} else {
			continue
		}

		switch header.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(targetPath, os.FileMode(header.Mode)); err != nil {
				return err
			}
		case tar.TypeReg:
			if err := os.MkdirAll(filepath.Dir(targetPath), 0755); err != nil {
				return err
			}

			outFile, err := os.OpenFile(targetPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, os.FileMode(header.Mode))
			if err != nil {
				return err
			}

			if _, err := io.Copy(outFile, tarReader); err != nil {
				outFile.Close()
				return err
			}
			outFile.Close()
		case tar.TypeSymlink:
			if err := os.MkdirAll(filepath.Dir(targetPath), 0755); err != nil {
				return err
			}
			os.Remove(targetPath)
			if err := os.Symlink(header.Linkname, targetPath); err != nil {
				return err
			}
		}
	}

	return nil
}

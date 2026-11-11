package command

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/templates"
)

type composeImage struct {
	service    string
	start, end int
	value      string
}

func upgradeInit(options *Options, folder string, out io.Writer) error {
	file := filepath.Join(folder, "compose.yaml")
	data, err := os.ReadFile(file)
	if os.IsNotExist(err) {
		return fmt.Errorf("%s does not exist: nothing to upgrade. Write it first with lvh-yard ws init", file)
	}
	if err != nil {
		return fmt.Errorf("Could not read %s: %w", file, err)
	}
	text := string(data)
	images := composeImages(text)
	services := []string{"consul", "traefik", "dashboard"}
	var missing []string
	for _, service := range services {
		if _, ok := findComposeImage(images, service); !ok {
			missing = append(missing, service)
		}
	}
	if len(missing) != 0 {
		return fmt.Errorf("%s has no image: line for %s: nothing was changed. Change its images by hand", file, strings.Join(missing, ", "))
	}
	template, err := templates.Compose(options.Version)
	if err != nil {
		return err
	}
	desired := composeImages(template)
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	for _, name := range []string{"consul.d", "traefik/dynamic", "tls"} {
		if err := os.MkdirAll(filepath.Join(folder, name), 0700); err != nil {
			return fmt.Errorf("could not create %s: %w", name, err)
		}
	}
	if err := os.WriteFile(filepath.Join(folder, "consul.d/00-lvh-yard.hcl"), []byte(templates.Consul()), 0600); err != nil {
		return fmt.Errorf("could not write fixed Consul options: %w", err)
	}
	if _, err := os.Stat(filepath.Join(folder, "consul.d", "10-tls.hcl")); err == nil {
		if err := writeConsulTLS(folder); err != nil { return err }
	} else if !os.IsNotExist(err) { return err }
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	changed := false
	for _, service := range services {
		from, _ := findComposeImage(images, service)
		to, ok := findComposeImage(desired, service)
		if !ok {
			return fmt.Errorf("compose.yaml template has no image: line for %s", service)
		}
		if from.value == to.value {
			fmt.Fprintf(out, "%s: %s, already this version's\n", service, to.value)
		} else {
			changed = true
			fmt.Fprintf(out, "%s: %s -> %s\n", service, from.value, to.value)
		}
	}
	if !changed {
		return nil
	}
	var updated strings.Builder
	updated.Grow(len(text))
	start := 0
	for _, image := range images {
		if image.service != "consul" && image.service != "traefik" && image.service != "dashboard" {
			continue
		}
		to, ok := findComposeImage(desired, image.service)
		if !ok || image.value == to.value {
			continue
		}
		updated.WriteString(text[start:image.start])
		updated.WriteString(to.value)
		start = image.end
	}
	updated.WriteString(text[start:])
	if err := os.WriteFile(file, []byte(updated.String()), 0600); err != nil {
		return fmt.Errorf("Could not write %s: %w", file, err)
	}
	fmt.Fprintf(out, "Upgraded %s. Start the new images: cd %s && docker compose up -d\n", file, folder)
	return nil
}

func findComposeImage(images []composeImage, service string) (composeImage, bool) {
	for _, image := range images {
		if image.service == service {
			return image, true
		}
	}
	return composeImage{}, false
}

// Only direct image values belong to a service. Offsets keep all other bytes intact.
func composeImages(text string) []composeImage {
	var images []composeImage
	inServices := false
	service := ""
	serviceIndent, propertyIndent := -1, -1
	for offset := 0; offset < len(text); {
		line := text[offset:]
		if end := strings.IndexByte(line, '\n'); end >= 0 {
			line = line[:end+1]
		}
		indent := len(line) - len(strings.TrimLeft(line, " "))
		key, rest, mapping := strings.Cut(strings.TrimRight(line[indent:], "\r\n"), ":")
		if mapping && key != "" && !strings.ContainsAny(key, " \t#'\"") {
			header := strings.TrimSpace(rest)
			isHeader := header == "" || strings.HasPrefix(header, "#")
			if indent == 0 {
				inServices = key == "services" && isHeader
				service, serviceIndent, propertyIndent = "", -1, -1
			} else if inServices {
				if serviceIndent < 0 {
					serviceIndent = indent
				}
				if indent <= serviceIndent {
					service, propertyIndent = "", -1
					if indent == serviceIndent && isHeader {
						service = key
					}
				} else if service != "" {
					if propertyIndent < 0 {
						propertyIndent = indent
					}
					if key == "image" && indent == propertyIndent {
						if _, found := findComposeImage(images, service); !found {
							if start, end, ok := composeImageValue(rest); ok {
								base := offset + indent + len(key) + 1
								images = append(images, composeImage{service, base + start, base + end, rest[start:end]})
							}
						}
					}
				}
			}
		}
		offset += len(line)
	}
	return images
}

func composeImageValue(rest string) (int, int, bool) {
	start := len(rest) - len(strings.TrimLeft(rest, " \t"))
	if start == len(rest) || strings.ContainsAny(rest[start:start+1], "#|>") {
		return 0, 0, false
	}
	end := start
	if quote := rest[start]; quote == '\'' || quote == '"' {
		start++
		closing := strings.IndexByte(rest[start:], quote)
		if closing < 0 {
			return 0, 0, false
		}
		end = start + closing
		tail := strings.TrimSpace(rest[end+1:])
		return start, end, end > start && (tail == "" || strings.HasPrefix(tail, "#"))
	}
	for end < len(rest) && rest[end] != ' ' && rest[end] != '\t' {
		end++
	}
	tail := strings.TrimSpace(rest[end:])
	return start, end, end > start && (tail == "" || strings.HasPrefix(tail, "#"))
}

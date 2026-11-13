// Package composefile finds the image: values of the services in a compose.yaml, and replaces them
// without changing any other byte. It is no YAML parser: it reads only the shape that LVH Yard
// writes, with one service for each key under services:.
package composefile

import (
	"slices"
	"strings"
)

// Image is the image: value of one service, by its place in the text.
type Image struct {
	Service    string
	Start, End int
	Value      string
}

// Find is the image of a service.
func Find(images []Image, service string) (Image, bool) {
	for _, image := range images {
		if image.Service == service {
			return image, true
		}
	}
	return Image{}, false
}

// Images gives the first direct image: value of each service. Offsets keep all other bytes intact.
func Images(text string) []Image {
	s := scanner{serviceIndent: -1, propertyIndent: -1}
	for offset := 0; offset < len(text); {
		line := text[offset:]
		if end := strings.IndexByte(line, '\n'); end >= 0 {
			line = line[:end+1]
		}
		s.read(offset, line)
		offset += len(line)
	}
	return s.images
}

// Replace gives text with the value of each image of the services that desired has other, and
// every other byte as it was. images are the images of text.
func Replace(text string, images, desired []Image, services []string) string {
	var updated strings.Builder
	updated.Grow(len(text))
	start := 0
	for _, image := range images {
		if !slices.Contains(services, image.Service) {
			continue
		}
		to, ok := Find(desired, image.Service)
		if !ok || image.Value == to.Value {
			continue
		}
		updated.WriteString(text[start:image.Start])
		updated.WriteString(to.Value)
		start = image.End
	}
	updated.WriteString(text[start:])
	return updated.String()
}

// scanner follows the indentation of the lines: the services: key, then one key for each service,
// then the properties of that service.
type scanner struct {
	images                        []Image
	inServices                    bool
	service                       string
	serviceIndent, propertyIndent int
}

func (s *scanner) read(offset int, line string) {
	indent := len(line) - len(strings.TrimLeft(line, " "))
	key, rest, mapping := strings.Cut(strings.TrimRight(line[indent:], "\r\n"), ":")
	if !mapping || key == "" || strings.ContainsAny(key, " \t#'\"") {
		return
	}
	header := strings.TrimSpace(rest)
	isHeader := header == "" || strings.HasPrefix(header, "#")
	switch {
	case indent == 0:
		s.inServices = key == "services" && isHeader
		s.service, s.serviceIndent, s.propertyIndent = "", -1, -1
	case s.inServices:
		s.underServices(offset, indent, key, rest, isHeader)
	}
}

func (s *scanner) underServices(offset, indent int, key, rest string, isHeader bool) {
	if s.serviceIndent < 0 {
		s.serviceIndent = indent
	}
	if indent <= s.serviceIndent {
		s.service, s.propertyIndent = "", -1
		if indent == s.serviceIndent && isHeader {
			s.service = key
		}
		return
	}
	if s.service != "" {
		s.property(offset, indent, key, rest)
	}
}

func (s *scanner) property(offset, indent int, key, rest string) {
	if s.propertyIndent < 0 {
		s.propertyIndent = indent
	}
	if key != "image" || indent != s.propertyIndent {
		return
	}
	if _, found := Find(s.images, s.service); found {
		return
	}
	if start, end, ok := imageValue(rest); ok {
		base := offset + indent + len(key) + 1
		s.images = append(s.images, Image{s.service, base + start, base + end, rest[start:end]})
	}
}

// imageValue is the place of the value in the text after "image:". A value with a quote ends at the
// closing quote. Anything but a comment after the value, and a block scalar, are no direct value.
func imageValue(rest string) (int, int, bool) {
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
		return start, end, end > start && onlyComment(rest[end+1:])
	}
	for end < len(rest) && rest[end] != ' ' && rest[end] != '\t' {
		end++
	}
	return start, end, end > start && onlyComment(rest[end:])
}

func onlyComment(tail string) bool {
	tail = strings.TrimSpace(tail)
	return tail == "" || strings.HasPrefix(tail, "#")
}

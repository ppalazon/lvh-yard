package command

import (
	"bytes"
	"errors"
	"fmt"
	"io"

	"go.yaml.in/yaml/v3"
)

var errProfileFileShape = errors.New("The file must hold only http.middlewares, with each profile's name and its middleware.")

type profileFile struct {
	profiles map[string]any
	names    []string
}

func readProfileFile(text []byte) (profileFile, error) {
	decoder := yaml.NewDecoder(bytes.NewReader(text))
	var document yaml.Node
	if err := decoder.Decode(&document); err != nil {
		if errors.Is(err, io.EOF) {
			return profileFile{}, errProfileFileShape
		}
		return profileFile{}, fmt.Errorf("The file is not valid YAML: %w", err)
	}
	var extra yaml.Node
	if err := decoder.Decode(&extra); !errors.Is(err, io.EOF) {
		if err != nil {
			return profileFile{}, fmt.Errorf("The file is not valid YAML: %w", err)
		}
		return profileFile{}, errProfileFileShape
	}
	// Decoding also rejects duplicate keys, which must not silently replace profiles.
	var decoded map[string]any
	if err := document.Decode(&decoded); err != nil {
		return profileFile{}, fmt.Errorf("The file is not valid YAML: %w", err)
	}
	if len(document.Content) != 1 {
		return profileFile{}, errProfileFileShape
	}
	root := profileMapping(document.Content[0])
	if root == nil || len(root.Content) != 2 || root.Content[0].Value != "http" {
		return profileFile{}, errProfileFileShape
	}
	http := profileMapping(root.Content[1])
	if http == nil || len(http.Content) != 2 || http.Content[0].Value != "middlewares" {
		return profileFile{}, errProfileFileShape
	}
	middlewares := profileMapping(http.Content[1])
	if middlewares == nil {
		return profileFile{}, errProfileFileShape
	}
	if len(middlewares.Content) == 0 {
		return profileFile{}, errors.New("The file names no Route Profile.")
	}
	profiles, ok := decoded["http"].(map[string]any)
	if !ok {
		return profileFile{}, errProfileFileShape
	}
	definitions, ok := profiles["middlewares"].(map[string]any)
	if !ok {
		return profileFile{}, errProfileFileShape
	}
	result := profileFile{profiles: definitions, names: make([]string, 0, len(definitions))}
	for i := 0; i < len(middlewares.Content); i += 2 {
		name := middlewares.Content[i]
		if name.Kind != yaml.ScalarNode || name.Tag != "!!str" {
			return profileFile{}, errProfileFileShape
		}
		result.names = append(result.names, name.Value)
	}
	return result, nil
}

func profileMapping(node *yaml.Node) *yaml.Node {
	if node.Kind == yaml.AliasNode {
		node = node.Alias
	}
	if node == nil || node.Kind != yaml.MappingNode {
		return nil
	}
	return node
}

func writeProfileFile(name string, definition map[string]any) ([]byte, error) {
	var text bytes.Buffer
	encoder := yaml.NewEncoder(&text)
	encoder.SetIndent(2)
	// The YAML encoder quotes strings such as 100, true and null for a safe round trip.
	if err := encoder.Encode(map[string]any{"http": map[string]any{"middlewares": map[string]any{name: definition}}}); err != nil {
		return nil, fmt.Errorf("Cannot write the Route Profile: %w", err)
	}
	if err := encoder.Close(); err != nil {
		return nil, fmt.Errorf("Cannot write the Route Profile: %w", err)
	}
	return text.Bytes(), nil
}

package command

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"strconv"

	"go.yaml.in/yaml/v3"
)

var errProfileFileShape = errors.New("The file must hold only http.middlewares, with one entry for each Route Profile.")

type profileFile struct {
	profiles map[string]any
	names    []string
}

func readProfileFile(text []byte) (profileFile, error) {
	document, decoded, err := decodeProfileDocument(text)
	if err != nil {
		return profileFile{}, err
	}
	middlewares, err := profileMiddlewaresNode(document)
	if err != nil {
		return profileFile{}, err
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

// decodeProfileDocument reads one YAML document, as a node tree and as a map.
func decodeProfileDocument(text []byte) (yaml.Node, map[string]any, error) {
	decoder := yaml.NewDecoder(bytes.NewReader(text))
	var document yaml.Node
	if err := decoder.Decode(&document); err != nil {
		if errors.Is(err, io.EOF) {
			return document, nil, errProfileFileShape
		}
		return document, nil, fmt.Errorf("The file is not valid YAML: %w", err)
	}
	var extra yaml.Node
	if err := decoder.Decode(&extra); !errors.Is(err, io.EOF) {
		if err != nil {
			return document, nil, fmt.Errorf("The file is not valid YAML: %w", err)
		}
		return document, nil, errProfileFileShape
	}
	// Decoding also rejects duplicate keys, which must not silently replace profiles.
	var decoded map[string]any
	if err := document.Decode(&decoded); err != nil {
		return document, nil, fmt.Errorf("The file is not valid YAML: %w", err)
	}
	return document, decoded, nil
}

// profileMiddlewaresNode returns the http.middlewares mapping of the document.
func profileMiddlewaresNode(document yaml.Node) (*yaml.Node, error) {
	if len(document.Content) != 1 {
		return nil, errProfileFileShape
	}
	root := profileMapping(document.Content[0])
	if root == nil || len(root.Content) != 2 || root.Content[0].Value != "http" {
		return nil, errProfileFileShape
	}
	http := profileMapping(root.Content[1])
	if http == nil || len(http.Content) != 2 || http.Content[0].Value != "middlewares" {
		return nil, errProfileFileShape
	}
	middlewares := profileMapping(http.Content[1])
	if middlewares == nil {
		return nil, errProfileFileShape
	}
	if len(middlewares.Content) == 0 {
		return nil, errors.New("The file names no Route Profile.")
	}
	return middlewares, nil
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
	if err := encoder.Encode(map[string]any{"http": map[string]any{"middlewares": map[string]any{name: plainNumbers(definition)}}}); err != nil {
		return nil, fmt.Errorf("Cannot write the Route Profile: %w", err)
	}
	if err := encoder.Close(); err != nil {
		return nil, fmt.Errorf("Cannot write the Route Profile: %w", err)
	}
	return text.Bytes(), nil
}

// plainNumbers turns JSON numbers into Go numbers. The YAML encoder writes a
// json.Number as a quoted string, and a float64 as 2e+06.
func plainNumbers(value any) any {
	switch value := value.(type) {
	case map[string]any:
		converted := make(map[string]any, len(value))
		for key, item := range value {
			converted[key] = plainNumbers(item)
		}
		return converted
	case []any:
		converted := make([]any, len(value))
		for i, item := range value {
			converted[i] = plainNumbers(item)
		}
		return converted
	case json.Number:
		if integer, err := value.Int64(); err == nil {
			return integer
		}
		if unsigned, err := strconv.ParseUint(value.String(), 10, 64); err == nil {
			return unsigned
		}
		if float, err := value.Float64(); err == nil {
			return float
		}
	}
	return value
}

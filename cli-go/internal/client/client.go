package client

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const connectTimeout = 2 * time.Second
const answerTimeout = 10 * time.Second

type Client struct {
	address string
	http    *http.Client
}

func New(address string) *Client {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.DialContext = (&net.Dialer{Timeout: connectTimeout, KeepAlive: 30 * time.Second}).DialContext
	return &Client{address: strings.TrimRight(address, "/") + "/", http: &http.Client{Transport: transport, Timeout: answerTimeout}}
}

// segment keeps a name inside one path segment, including names with slashes.
func segment(value string) string { return url.PathEscape(value) }

func (c *Client) request(method, path string, body any, out any) error {
	var input io.Reader
	contentType := ""
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return fmt.Errorf("Cannot encode the dashboard request: %w", err)
		}
		input = bytes.NewReader(data)
		contentType = "application/json"
	}
	data, err := c.exchange(method, path, contentType, "application/json", input)
	if err != nil {
		return err
	}
	if out != nil {
		if err := json.Unmarshal(data, out); err != nil {
			return fmt.Errorf("%s is not the LVH Yard dashboard: its answer is not the API's JSON.", c.address)
		}
	}
	return nil
}

func (c *Client) exchange(method, path, contentType, accept string, body io.Reader) ([]byte, error) {
	request, err := http.NewRequest(method, c.address+strings.TrimLeft(path, "/"), body)
	if err != nil {
		return nil, fmt.Errorf("The dashboard address is not valid: %s", c.address)
	}
	request.Header.Set("Accept", accept)
	if contentType != "" {
		request.Header.Set("Content-Type", contentType)
	}
	response, err := c.http.Do(request)
	if err != nil {
		return nil, c.connectionError(err)
	}
	defer response.Body.Close()
	data, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, c.connectionError(err)
	}
	if response.StatusCode >= 400 {
		return nil, problemReason(response.StatusCode, data)
	}
	return data, nil
}

func (c *Client) connectionError(err error) error {
	var timeout net.Error
	if errors.As(err, &timeout) && timeout.Timeout() {
		return fmt.Errorf("The dashboard at %s did not answer within 10 s. Make sure that Consul runs: docker compose up -d in the folder that lvh-yard init wrote.", c.address)
	}
	var operation *net.OpError
	if errors.As(err, &operation) && operation.Op == "dial" {
		return fmt.Errorf("The dashboard does not answer at %s. Start it with docker compose up -d in the folder that lvh-yard init wrote.", c.address)
	}
	return fmt.Errorf("The dashboard at %s cannot be reached: %w", c.address, err)
}

func problemReason(status int, data []byte) error {
	var problem struct {
		Detail   string `json:"detail"`
		Title    string `json:"title"`
		Problems []struct {
			Place  string `json:"place"`
			Reason string `json:"reason"`
		} `json:"problems"`
	}
	if json.Unmarshal(data, &problem) == nil {
		reasons := make([]string, 0, len(problem.Problems))
		for _, item := range problem.Problems {
			if item.Reason == "" {
				reasons = nil
				break
			}
			reason := item.Reason
			if item.Place != "" {
				reason = item.Place + ": " + reason
			}
			reasons = append(reasons, reason)
		}
		if len(reasons) > 0 {
			return errors.New(strings.Join(reasons, "\n"))
		}
		if strings.TrimSpace(problem.Detail) != "" {
			return errors.New(problem.Detail)
		}
		if strings.TrimSpace(problem.Title) != "" {
			return errors.New(problem.Title)
		}
	}
	return fmt.Errorf("The dashboard refused with HTTP status %d.", status)
}

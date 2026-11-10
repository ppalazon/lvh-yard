package client

import (
	"errors"
	"net/http"
)

func (c *Client) OwnServices() ([]Service, error) {
	var services []Service
	err := c.request(http.MethodGet, "api/workstation/services", nil, &services)
	return services, err
}

func (c *Client) OwnService(service string) (Service, error) {
	var found Service
	err := c.request(http.MethodGet, servicePath(service), nil, &found)
	return found, err
}

// CreateService reports whether the Service was new, not whether it is empty.
func (c *Client) CreateService(service string) (bool, error) {
	status, err := c.requestStatus(http.MethodPost, "api/workstation/services", NewServiceRequest{Name: service}, nil)
	return status == http.StatusCreated, err
}

// DeleteService reports whether the Service existed. Deleting a missing Service is not an error.
func (c *Client) DeleteService(service string) (bool, error) {
	err := c.request(http.MethodDelete, servicePath(service), nil, nil)
	var response *responseError
	if errors.As(err, &response) && response.status == http.StatusNotFound {
		return false, nil
	}
	return err == nil, err
}

func servicePath(service string) string {
	return "api/workstation/services/" + segment(service)
}

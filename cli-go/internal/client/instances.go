package client

import (
	"errors"
	"fmt"
	"net/http"
)

func (c *Client) Register(service string, request StaticInstanceRequest) (Instance, error) {
	var instance Instance
	err := c.request(http.MethodPost, servicePath(service)+"/instances", request, &instance)
	return instance, attachmentError(err)
}

func (c *Client) EditInstance(service, id string, request StaticInstanceRequest) (Instance, error) {
	var instance Instance
	err := c.request(http.MethodPut, servicePath(service)+"/instances/"+segment(id), request, &instance)
	return instance, err
}

func (c *Client) Remove(service, id string) error {
	return c.request(http.MethodDelete, servicePath(service)+"/instances/"+segment(id), nil, nil)
}

func (c *Client) UnbindContainer(service, id string) error {
	return c.request(http.MethodDelete, servicePath(service)+"/container-bindings/"+segment(id), nil, nil)
}

func attachmentError(err error) error {
	var response *responseError
	if errors.As(err, &response) && response.status == http.StatusNotFound && response.service != "" {
		return fmt.Errorf("%w Create it first: lvh-yard service create %s", err, response.service)
	}
	return err
}

package client

import "net/http"

func (c *Client) Publish(service string, request PublicationRequest) (Service, error) {
	var body any
	if request.Scope != nil || request.Profiles != nil || request.AccessLog != nil {
		body = request
	}
	var published Service
	err := c.request(http.MethodPut, servicePath(service)+"/publication", body, &published)
	return published, err
}

func (c *Client) Unpublish(service string) error {
	return c.request(http.MethodDelete, servicePath(service)+"/publication", nil, nil)
}

package client

import "net/http"

func (c *Client) AddAlias(service string, request AliasRequest) (AddedAlias, error) {
	var added AddedAlias
	err := c.request(http.MethodPost, servicePath(service)+"/aliases", request, &added)
	return added, err
}

func (c *Client) Aliases(service string) ([]Alias, error) {
	var aliases []Alias
	err := c.request(http.MethodGet, servicePath(service)+"/aliases", nil, &aliases)
	return aliases, err
}

func (c *Client) RemoveAlias(service, workstation, remoteService string) error {
	return c.request(http.MethodDelete, servicePath(service)+"/aliases/"+segment(workstation)+"/"+segment(remoteService), nil, nil)
}

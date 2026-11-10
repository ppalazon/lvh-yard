package client

import "net/http"

func (c *Client) Containers() ([]ListedContainer, error) {
	var containers []ListedContainer
	err := c.request(http.MethodGet, "api/workstation/containers", nil, &containers)
	return containers, err
}

func (c *Client) BindContainer(service, container string, port int) (ContainerBinding, error) {
	return c.bindContainer(service, ContainerBindingRequest{Container: &container, Port: port})
}

func (c *Client) BindCompose(service, project, composeService string, port int) (ContainerBinding, error) {
	return c.bindContainer(service, ContainerBindingRequest{
		ComposeProject: &project,
		ComposeService: &composeService,
		Port:           port,
	})
}

func (c *Client) bindContainer(service string, request ContainerBindingRequest) (ContainerBinding, error) {
	var binding ContainerBinding
	err := c.request(http.MethodPost, servicePath(service)+"/container-bindings", request, &binding)
	return binding, attachmentError(err)
}

package client

import "net/http"

func (c *Client) InstancePriority(service string) (InstancePriority, error) {
	var priority InstancePriority
	err := c.request(http.MethodGet, servicePath(service)+"/instance-priority", nil, &priority)
	return priority, err
}

func (c *Client) SetInstancePriority(service string, request InstancePriorityRequest) (InstancePriority, error) {
	var priority InstancePriority
	err := c.request(http.MethodPut, servicePath(service)+"/instance-priority", request, &priority)
	return priority, err
}

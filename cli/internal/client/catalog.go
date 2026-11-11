package client

import "net/url"

func (c *Client) Yard() (Yard, error) {
	var yard Yard
	err := c.request("GET", "api/yard", nil, &yard)
	return yard, err
}

func (c *Client) YardServices(workstation, state string) ([]Service, error) {
	query := url.Values{}
	if workstation != "" {
		query.Set("workstation", workstation)
	}
	if state != "" {
		query.Set("state", state)
	}
	path := "api/services"
	if len(query) != 0 {
		path += "?" + query.Encode()
	}
	var services []Service
	err := c.request("GET", path, nil, &services)
	return services, err
}

func (c *Client) YardService(workstation, service string) (Service, error) {
	var result Service
	err := c.request("GET", "api/services/"+segment(workstation)+"/"+segment(service), nil, &result)
	return result, err
}

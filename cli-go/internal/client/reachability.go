package client

func (c *Client) TestOwn(service string) (Reachability, error) {
	var result Reachability
	err := c.request("GET", "api/workstation/services/"+segment(service)+"/reachability", nil, &result)
	return result, err
}

func (c *Client) Test(workstation, service string) (Reachability, error) {
	var result Reachability
	err := c.request("GET", "api/services/"+segment(workstation)+"/"+segment(service)+"/reachability", nil, &result)
	return result, err
}

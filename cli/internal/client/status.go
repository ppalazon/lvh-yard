package client

func (c *Client) Status() (Status, error) {
	var status Status
	err := c.request("GET", "api/status", nil, &status)
	return status, err
}

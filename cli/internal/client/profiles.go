package client

func (c *Client) RouteProfiles() ([]RouteProfile, error) {
	var profiles []RouteProfile
	err := c.request("GET", "api/workstation/route-profiles", nil, &profiles)
	return profiles, err
}

func (c *Client) RouteProfile(name string) (RouteProfileDetail, error) {
	var profile RouteProfileDetail
	err := c.request("GET", routeProfilePath(name), nil, &profile)
	return profile, err
}

func (c *Client) PutRouteProfiles(profiles map[string]any) ([]RouteProfile, error) {
	var saved []RouteProfile
	err := c.request("PUT", "api/workstation/route-profiles", profiles, &saved)
	return saved, err
}

func (c *Client) PutRouteProfile(name string, middleware any) (RouteProfileDetail, error) {
	var profile RouteProfileDetail
	err := c.request("PUT", routeProfilePath(name), middleware, &profile)
	return profile, err
}

func (c *Client) RemoveRouteProfile(name string) error {
	return c.request("DELETE", routeProfilePath(name), nil, nil)
}

func routeProfilePath(name string) string {
	return "api/workstation/route-profiles/" + segment(name)
}

package command

import "github.com/ppalazon/lvh-yard/cli/internal/client"

// Dashboard is what the commands ask of the dashboard. Options.Dashboard builds it for an address.
// client.Client is the real one. A test fake embeds Dashboard and gives only the methods that its
// command calls.
type Dashboard interface {
	// alias
	AddAlias(service string, request client.AliasRequest) (client.AddedAlias, error)
	Aliases(service string) ([]client.Alias, error)
	RemoveAlias(service, workstation, remoteService string) error
	// export and import
	Backup() (string, error)
	ImportBackup(backup, mode string, dryRun bool) (client.ImportResult, error)
	// catalog
	Yard() (client.Yard, error)
	// containers and Container Bindings
	Containers() ([]client.ListedContainer, error)
	BindContainer(service, container string, port int) (client.ContainerBinding, error)
	BindCompose(service, project, composeService string, port int) (client.ContainerBinding, error)
	UnbindContainer(service, id string) error
	// Static Instances
	Register(service string, request client.StaticInstanceRequest) (client.Instance, error)
	Remove(service, id string) error
	// Instance Priority
	OwnService(service string) (client.Service, error)
	SetInstancePriority(service string, request client.InstancePriorityRequest) (client.InstancePriority, error)
	// Route Profiles
	RouteProfiles() ([]client.RouteProfile, error)
	RouteProfile(name string) (client.RouteProfileDetail, error)
	PutRouteProfiles(profiles map[string]any) ([]client.RouteProfile, error)
	PutRouteProfile(name string, middleware any) (client.RouteProfileDetail, error)
	RemoveRouteProfile(name string) error
	// Publications
	Publish(service string, request client.PublicationRequest) (client.Service, error)
	Unpublish(service string) error
	// reachability
	Test(workstation, service string) (client.Reachability, error)
	TestOwn(service string) (client.Reachability, error)
	// Services
	CreateService(service string) (bool, error)
	DeleteService(service string) (bool, error)
	// status
	Status() (client.Status, error)
}

var _ Dashboard = (*client.Client)(nil)

func newDashboard(address string) Dashboard {
	return client.New(address)
}

// connect is the dashboard that the --dashboard flag, the environment or the Workstation Folder names.
func (o *Options) connect() Dashboard {
	return o.Dashboard(o.DashboardAddress())
}

package client

type AddedAlias struct {
	AliasCreated   bool   `json:"aliasCreated"`
	ID             string `json:"id"`
	Reason         string `json:"reason,omitempty"`
	Service        string `json:"service"`
	ServiceCreated bool   `json:"serviceCreated"`
	State          string `json:"state"`
	Workstation    string `json:"workstation"`
}

type Alias struct {
	ID          string `json:"id"`
	Reason      string `json:"reason,omitempty"`
	Service     string `json:"service"`
	State       string `json:"state"`
	Workstation string `json:"workstation"`
}

type AliasRequest struct {
	Create      *bool  `json:"create,omitempty"`
	Service     string `json:"service"`
	Workstation string `json:"workstation"`
}

type CheckResult struct {
	Health string `json:"health"`
	Name   string `json:"name"`
	Output string `json:"output"`
}

type ContainerBinding struct {
	ComposeProject string   `json:"composeProject,omitempty"`
	ComposeService string   `json:"composeService,omitempty"`
	Container      string   `json:"container,omitempty"`
	ID             string   `json:"id"`
	Instances      []string `json:"instances"`
	Port           int      `json:"port"`
}

type ContainerBindingRequest struct {
	ComposeProject *string `json:"composeProject,omitempty"`
	ComposeService *string `json:"composeService,omitempty"`
	Container      *string `json:"container,omitempty"`
	Port           int     `json:"port"`
}

type ContainerPort struct {
	ContainerPort int    `json:"containerPort"`
	HostIP        string `json:"hostIp,omitempty"`
	HostPort      *int   `json:"hostPort,omitempty"`
}

type ExportedFrom struct {
	At          string `json:"at,omitempty"`
	Workstation string `json:"workstation,omitempty"`
}

type HealthCheck struct {
	Interval string `json:"interval,omitempty"`
	Path     string `json:"path,omitempty"`
	Timeout  string `json:"timeout,omitempty"`
	Type     string `json:"type,omitempty"`
}

type HomeWorkstation struct {
	Name     string    `json:"name"`
	Services []Service `json:"services"`
}

type ImportResult struct {
	DryRun       bool          `json:"dryRun"`
	Entries      []PlanEntry   `json:"entries"`
	ExportedFrom *ExportedFrom `json:"exportedFrom,omitempty"`
	Mode         string        `json:"mode"`
	SafetyBackup string        `json:"safetyBackup,omitempty"`
}

type Instance struct {
	Check                    *HealthCheck      `json:"check,omitempty"`
	Checks                   []CheckResult     `json:"checks"`
	Container                string            `json:"container,omitempty"`
	ContainerBinding         string            `json:"containerBinding,omitempty"`
	ContainerPort            *int              `json:"containerPort,omitempty"`
	Foreign                  bool              `json:"foreign,omitempty"`
	Health                   string            `json:"health"`
	Host                     string            `json:"host"`
	HostPort                 *int              `json:"hostPort,omitempty"`
	ID                       string            `json:"id"`
	Meta                     map[string]string `json:"meta"`
	OutOfRange               bool              `json:"outOfRange"`
	Port                     int               `json:"port"`
	Tags                     []string          `json:"tags"`
	UnpublishedContainerPort bool              `json:"unpublishedContainerPort,omitempty"`
}

type InstancePriority struct {
	On      bool             `json:"on"`
	Routing *Routing         `json:"routing,omitempty"`
	Sources []InstanceSource `json:"sources"`
}

type InstancePriorityRequest struct {
	On    *bool     `json:"on,omitempty"`
	Order *[]string `json:"order,omitempty"`
}

type InstanceReachability struct {
	Address    string `json:"address"`
	Error      string `json:"error,omitempty"`
	ID         string `json:"id"`
	OutOfRange bool   `json:"outOfRange,omitempty"`
	Reached    bool   `json:"reached"`
	Routed     bool   `json:"routed,omitempty"`
}

type InstanceSource struct {
	ID        string   `json:"id"`
	Instances []string `json:"instances"`
	Kind      string   `json:"kind"`
}

type ListedContainer struct {
	ComposeProject string          `json:"composeProject,omitempty"`
	ComposeService string          `json:"composeService,omitempty"`
	FollowedBy     []string        `json:"followedBy"`
	Health         string          `json:"health,omitempty"`
	Name           string          `json:"name"`
	Ports          []ContainerPort `json:"ports"`
	Running        bool            `json:"running"`
	Service        string          `json:"service"`
}

type NewServiceRequest struct {
	Name string `json:"name"`
}

type PlanEntry struct {
	Action  string `json:"action"`
	Kind    string `json:"kind"`
	Name    string `json:"name"`
	Reason  string `json:"reason,omitempty"`
	Service string `json:"service,omitempty"`
}

type ProblemDetail struct {
	Detail   string `json:"detail,omitempty"`
	Instance string `json:"instance,omitempty"`
	Status   int    `json:"status,omitempty"`
	Title    string `json:"title,omitempty"`
	Type     string `json:"type,omitempty"`
}

type Publication struct {
	AccessLog bool   `json:"accessLog"`
	Scope     string `json:"scope"`
}

type PublicationRequest struct {
	AccessLog *bool     `json:"accessLog,omitempty"`
	Profiles  *[]string `json:"profiles,omitempty"`
	Scope     *string   `json:"scope,omitempty"`
}

type Reachability struct {
	Alias       *Alias                 `json:"alias,omitempty"`
	Instances   []InstanceReachability `json:"instances"`
	NoRoute     string                 `json:"noRoute,omitempty"`
	Passed      bool                   `json:"passed"`
	Routes      []RouteReachability    `json:"routes"`
	Routing     *Routing               `json:"routing,omitempty"`
	Service     string                 `json:"service"`
	Workstation string                 `json:"workstation"`
}

type RouteProfile struct {
	Name     string   `json:"name"`
	Services []string `json:"services"`
	Type     string   `json:"type,omitempty"`
}

type RouteProfileDetail struct {
	Definition map[string]any `json:"definition"`
	Name       string         `json:"name"`
	Services   []string       `json:"services"`
	Type       string         `json:"type,omitempty"`
}

type RouteReachability struct {
	Error    string `json:"error,omitempty"`
	Hostname string `json:"hostname"`
	Reached  bool   `json:"reached"`
	Status   *int   `json:"status,omitempty"`
}

type Routing struct {
	Instances []string `json:"instances"`
	Kind      string   `json:"kind,omitempty"`
	Off       bool     `json:"off,omitempty"`
	Source    string   `json:"source,omitempty"`
	Waiting   []string `json:"waiting"`
}

type Service struct {
	ContainerBindings []ContainerBinding `json:"containerBindings"`
	Created           bool               `json:"created"`
	Empty             bool               `json:"empty"`
	Hostname          string             `json:"hostname,omitempty"`
	InstancePriority  bool               `json:"instancePriority"`
	Instances         []Instance         `json:"instances"`
	Name              string             `json:"name"`
	Profiles          []string           `json:"profiles,omitempty"`
	Publication       *Publication       `json:"publication,omitempty"`
	Routing           *Routing           `json:"routing,omitempty"`
	Sources           []InstanceSource   `json:"sources"`
	State             string             `json:"state"`
	Warnings          []ServiceWarning   `json:"warnings"`
	Workstation       string             `json:"workstation"`
}

type ServiceWarning struct {
	Kind     string `json:"kind"`
	Message  string `json:"message"`
	Severity string `json:"severity"`
}

type StaticInstanceRequest struct {
	Check *HealthCheck       `json:"check,omitempty"`
	Host  *string            `json:"host,omitempty"`
	Meta  *map[string]string `json:"meta,omitempty"`
	Port  int                `json:"port"`
	Tags  *[]string          `json:"tags,omitempty"`
}

type Status struct {
	Overall string       `json:"overall"`
	Parts   []StatusPart `json:"parts"`
}

type StatusDetail struct {
	Name  string `json:"name"`
	State string `json:"state,omitempty"`
	Value string `json:"value"`
}

type StatusPart struct {
	Details []StatusDetail `json:"details"`
	Name    string         `json:"name"`
	Reason  string         `json:"reason"`
	State   string         `json:"state"`
}

type Yard struct {
	Home    HomeWorkstation   `json:"home"`
	Remotes []YardWorkstation `json:"remotes"`
}

type YardWorkstation struct {
	Name        string    `json:"name"`
	Services    []Service `json:"services"`
	Unreachable bool      `json:"unreachable"`
}

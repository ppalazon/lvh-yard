package command

import (
	"bytes"
	"encoding/json"
	"fmt"

	"github.com/ppalazon/lvh-yard/cli/internal/certzip"
	"github.com/ppalazon/lvh-yard/cli/internal/settings"
)

// An invitation is the zip file that ws yard share makes for one Workstation and ws yard join reads.
// Its content is private: the Gossip Key of the Yard and the key of the Consul certificate. So the
// whole zip file is encrypted with a password.
const (
	yardInvitationFile    = "yard.json"
	yardInvitationVersion = 1
)

type yardInvitation struct {
	Version int `json:"version"`
	// From is the Workstation that shares, and To the one that joins. The Consul certificate names To.
	From string `json:"from"`
	To   string `json:"to"`
	// Peer is the address of From, as <ip>:<serf-wan-port>.
	Peer      string `json:"peer"`
	GossipKey string `json:"gossipKey"`
}

type receivedInvitation struct {
	yardInvitation
	caPEM, certPEM, keyPEM []byte
}

func invitationFiles(inv yardInvitation, caPEM, certPEM, keyPEM []byte, joinCommand string) ([]certzip.File, error) {
	manifest, err := json.MarshalIndent(inv, "", "  ")
	if err != nil {
		return nil, err
	}
	names := roleFiles(roleConsul)
	return []certzip.File{
		{Name: yardInvitationFile, Data: append(manifest, '\n')},
		{Name: roleConsul + "/" + names[0], Data: caPEM},
		{Name: roleConsul + "/" + names[1], Data: certPEM},
		{Name: roleConsul + "/" + names[2], Data: keyPEM},
		{Name: "README.txt", Data: []byte(invitationReadme(inv, joinCommand))},
	}, nil
}

// readInvitation reads the files of a zip file and refuses one that is not an invitation.
func readInvitation(files []certzip.File) (received receivedInvitation, err error) {
	byName := map[string][]byte{}
	for _, file := range files {
		byName[file.Name] = file.Data
	}
	names := roleFiles(roleConsul)
	manifest, ok := byName[yardInvitationFile]
	if !ok {
		return received, fmt.Errorf("the zip file is not a Yard Invitation: it has no %s", yardInvitationFile)
	}
	for i, target := range []*[]byte{&received.caPEM, &received.certPEM, &received.keyPEM} {
		path := roleConsul + "/" + names[i]
		if *target, ok = byName[path]; !ok {
			return received, fmt.Errorf("the Yard Invitation has no %s", path)
		}
	}
	decoder := json.NewDecoder(bytes.NewReader(manifest))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&received.yardInvitation); err != nil {
		return received, fmt.Errorf("the Yard Invitation is damaged: %w", err)
	}
	inv := received.yardInvitation
	if inv.Version != yardInvitationVersion {
		return received, fmt.Errorf("the Yard Invitation has version %d, and this lvh-yard reads version %d. Update lvh-yard", inv.Version, yardInvitationVersion)
	}
	if checkWorkstationName(inv.From) != nil || checkWorkstationName(inv.To) != nil || checkInitGossipKey(inv.GossipKey) != nil {
		return received, fmt.Errorf("the Yard Invitation is damaged: a name or the Gossip Key is not valid")
	}
	if _, err := settings.Check("peers", inv.Peer); err != nil || inv.Peer == "" {
		return received, fmt.Errorf("the Yard Invitation is damaged: the Peer address is not valid")
	}
	return received, nil
}

func invitationReadme(inv yardInvitation, joinCommand string) string {
	return fmt.Sprintf(`Invitation of %[2]s to the Yard of %[1]s

Holds:
  - the Gossip Key of the Yard
  - the Consul certificate and key of %[2]s
  - the Main CA certificate

Before:
  - Get the password of this zip file from the sender, by another way.

Steps, on %[2]s:
  1. Run this command with the path of this file. Do not unzip it:
       %[3]s
  2. Run the "compose recreate" command that it prints.
  3. Open the ports that it names in the firewall.

Note:
  - Never send this file or its password to anyone else.
`, inv.From, inv.To, joinCommand)
}

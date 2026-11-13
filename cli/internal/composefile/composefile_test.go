package composefile_test

import (
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/composefile"
)

func values(images []composefile.Image) map[string]string {
	found := map[string]string{}
	for _, image := range images {
		found[image.Service] = image.Value
	}
	return found
}

func TestImagesFindsTheDirectImageOfEachService(t *testing.T) {
	text := "name: yard\nservices:\n  consul:\n    image: hashicorp/consul:1.21\n    command: x\n  traefik: # proxy\n    image: 'traefik:v3'\n  dashboard:\n    image: \"ghcr.io/x/dash:1\" # pinned\nnetworks:\n  consul:\n    image: ignored\n"
	got := values(composefile.Images(text))
	want := map[string]string{"consul": "hashicorp/consul:1.21", "traefik": "traefik:v3", "dashboard": "ghcr.io/x/dash:1"}
	if len(got) != len(want) {
		t.Fatalf("Images = %v; want %v", got, want)
	}
	for service, value := range want {
		if got[service] != value {
			t.Errorf("%s = %q; want %q", service, got[service], value)
		}
	}
}

func TestImageOffsetsPointAtTheValueOnly(t *testing.T) {
	text := "services:\r\n  a:\r\n    image: 'x:1'\r\n  b:\r\n    image: y:2 # c\r\n"
	for _, image := range composefile.Images(text) {
		if text[image.Start:image.End] != image.Value {
			t.Errorf("%s: text[%d:%d] = %q; want %q", image.Service, image.Start, image.End, text[image.Start:image.End], image.Value)
		}
	}
}

func TestImagesSkipsWhatIsNoDirectValue(t *testing.T) {
	for name, text := range map[string]string{
		"block scalar":      "services:\n  a:\n    image: |\n      x\n",
		"comment":           "services:\n  a:\n    image: # none\n",
		"no value":          "services:\n  a:\n    image:\n",
		"open quote":        "services:\n  a:\n    image: \"x\n",
		"empty quotes":      "services:\n  a:\n    image: ''\n",
		"text after":        "services:\n  a:\n    image: x y\n",
		"not under service": "x:\n  a:\n    image: v\n",
		"service has value": "services:\n  a: 1\n    image: v\n",
	} {
		got := composefile.Images(text)
		if len(got) != 0 {
			t.Errorf("%s: Images = %v; want none", name, got)
		}
	}
}

func TestAnImageOfADeeperLevelIsNoImageOfTheService(t *testing.T) {
	got := composefile.Images("services:\n  a:\n    build:\n      image: x\n    image: ok\n")
	if len(got) != 1 || got[0].Value != "ok" {
		t.Fatalf("Images = %v", got)
	}
}

func TestOnlyTheFirstImageOfAServiceCounts(t *testing.T) {
	got := composefile.Images("services:\n  a:\n    image: first\n    image: second\n")
	if len(got) != 1 || got[0].Value != "first" {
		t.Fatalf("Images = %v", got)
	}
}

func TestFind(t *testing.T) {
	images := composefile.Images("services:\n  a:\n    image: x\n")
	if image, ok := composefile.Find(images, "a"); !ok || image.Value != "x" {
		t.Fatalf("Find(a) = %v, %v", image, ok)
	}
	if _, ok := composefile.Find(images, "b"); ok {
		t.Fatal("Find(b) found an image")
	}
}

func TestReplaceChangesOnlyTheValuesOfTheNamedServicesThatDiffer(t *testing.T) {
	text := "# keep\nservices:\n  consul:\n    image: c:1 # old\n  traefik:\n    image: \"t:1\"\n  other:\n    image: o:1\n"
	desired := composefile.Images("services:\n  consul:\n    image: c:2\n  traefik:\n    image: t:1\n  other:\n    image: o:2\n")
	got := composefile.Replace(text, composefile.Images(text), desired, []string{"consul", "traefik"})
	want := "# keep\nservices:\n  consul:\n    image: c:2 # old\n  traefik:\n    image: \"t:1\"\n  other:\n    image: o:1\n"
	if got != want {
		t.Fatalf("Replace =\n%q\nwant\n%q", got, want)
	}
}

func TestReplaceKeepsAServiceThatDesiredLacks(t *testing.T) {
	text := "services:\n  a:\n    image: x\n"
	if got := composefile.Replace(text, composefile.Images(text), nil, []string{"a"}); got != text {
		t.Fatalf("Replace = %q", got)
	}
}

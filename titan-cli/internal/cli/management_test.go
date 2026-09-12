package cli

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/traffic-hunter/titan/titan-cli/internal/monitor"
)

// scriptedPrompt drives the management menu without a terminal.
type scriptedPrompt struct {
	actions      []string
	groups       []string
	destinations []string
	confirms     []bool
	maxPending   int64
	acknowledged int
	confirmed    []string
}

func (p *scriptedPrompt) Action() (string, error) {
	if len(p.actions) == 0 {
		return managementBack, nil
	}
	action := p.actions[0]
	p.actions = p.actions[1:]
	return action, nil
}

func (p *scriptedPrompt) Group(string) (string, error) {
	if len(p.groups) == 0 {
		return "", nil
	}
	group := p.groups[0]
	p.groups = p.groups[1:]
	return group, nil
}

func (p *scriptedPrompt) Destination(string) (string, error) {
	if len(p.destinations) == 0 {
		return "", nil
	}
	destination := p.destinations[0]
	p.destinations = p.destinations[1:]
	return destination, nil
}

func (p *scriptedPrompt) MaxPendingBytes() (int64, error) {
	return p.maxPending, nil
}

func (p *scriptedPrompt) Confirm(prompt string) (bool, error) {
	p.confirmed = append(p.confirmed, prompt)
	if len(p.confirms) == 0 {
		return false, nil
	}
	confirmed := p.confirms[0]
	p.confirms = p.confirms[1:]
	return confirmed, nil
}

func (p *scriptedPrompt) Acknowledge() error {
	p.acknowledged++
	return nil
}

func TestMainMenuIncludesManagement(t *testing.T) {
	var found bool
	for _, option := range mainMenuOptions() {
		if option.Value == "management" {
			found = true
			if option.Key != "Management" {
				t.Fatalf("expected Management label, got %q", option.Key)
			}
		}
	}
	if !found {
		t.Fatalf("expected management entry in main menu")
	}
}

func TestManagementMenuIncludesEveryQueueAction(t *testing.T) {
	want := []string{
		managementList,
		managementCreate,
		managementPause,
		managementResume,
		managementPurge,
		managementDelete,
		managementBack,
	}
	options := managementActionOptions()
	if len(options) != len(want) {
		t.Fatalf("expected %d options, got %d", len(want), len(options))
	}
	for i, value := range want {
		if options[i].Value != value {
			t.Fatalf("expected option %d to be %q, got %q", i, value, options[i].Value)
		}
	}
}

func TestManagementLoopReturnsOnBackWithoutRequests(t *testing.T) {
	var requests int
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
	}))
	defer server.Close()

	prompt := &scriptedPrompt{actions: []string{managementBack}}
	var out bytes.Buffer

	err := runManagementLoop(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out, false)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if requests != 0 {
		t.Fatalf("expected no requests, got %d", requests)
	}
}

func TestManagementLoopRunsActionsThenReturnsOnBack(t *testing.T) {
	var seen []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodGet {
			seen = append(seen, "list")
			_, _ = w.Write([]byte(`[]`))
			return
		}
		seen = append(seen, r.URL.Query().Get("action"))
	}))
	defer server.Close()

	prompt := &scriptedPrompt{
		actions:      []string{managementList, managementPause, managementResume, managementBack},
		destinations: []string{"/queue/orders", "/queue/orders"},
	}
	var out bytes.Buffer

	err := runManagementLoop(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out, false)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if strings.Join(seen, ",") != "list,pause,resume" {
		t.Fatalf("unexpected requests %v", seen)
	}
	if prompt.acknowledged != 1 {
		t.Fatalf("expected list to wait for acknowledgement once, got %d", prompt.acknowledged)
	}
	if !strings.Contains(out.String(), "paused default:/queue/orders") {
		t.Fatalf("expected pause output, got %q", out.String())
	}
}

func TestPurgeSendsNoRequestWhenConfirmationIsDeclined(t *testing.T) {
	var requests int
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
	}))
	defer server.Close()

	prompt := &scriptedPrompt{destinations: []string{"/queue/orders"}, confirms: []bool{false}}
	var out bytes.Buffer

	err := purgeQueueInteractive(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if requests != 0 {
		t.Fatalf("expected no requests, got %d", requests)
	}
	if len(prompt.confirmed) != 1 ||
		prompt.confirmed[0] != "Remove every pending message from default:/queue/orders?" {
		t.Fatalf("unexpected confirmation prompt %v", prompt.confirmed)
	}
	if !strings.Contains(out.String(), "cancelled purge") {
		t.Fatalf("expected cancellation notice, got %q", out.String())
	}
}

func TestPurgeSendsRequestWhenConfirmed(t *testing.T) {
	var action string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		action = r.URL.Query().Get("action")
	}))
	defer server.Close()

	prompt := &scriptedPrompt{destinations: []string{"/queue/orders"}, confirms: []bool{true}}
	var out bytes.Buffer

	if err := purgeQueueInteractive(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if action != "purge" {
		t.Fatalf("expected purge action, got %q", action)
	}
	if !strings.Contains(out.String(), "purged default:/queue/orders") {
		t.Fatalf("expected purge output, got %q", out.String())
	}
}

func TestDeleteEmptyQueueSkipsForceConfirmation(t *testing.T) {
	var forces []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		forces = append(forces, r.URL.Query().Get("force"))
	}))
	defer server.Close()

	prompt := &scriptedPrompt{destinations: []string{"/queue/orders"}}
	var out bytes.Buffer

	if err := deleteQueueInteractive(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if strings.Join(forces, ",") != "false" {
		t.Fatalf("expected a single non-force delete, got %v", forces)
	}
	if len(prompt.confirmed) != 0 {
		t.Fatalf("expected no confirmation for an empty queue, got %v", prompt.confirmed)
	}
}

func TestDeleteNonEmptyQueueStopsWhenForceIsDeclined(t *testing.T) {
	var forces []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		forces = append(forces, r.URL.Query().Get("force"))
		w.WriteHeader(http.StatusConflict)
	}))
	defer server.Close()

	prompt := &scriptedPrompt{destinations: []string{"/queue/orders"}, confirms: []bool{false}}
	var out bytes.Buffer

	if err := deleteQueueInteractive(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if strings.Join(forces, ",") != "false" {
		t.Fatalf("expected only the non-force delete attempt, got %v", forces)
	}
	if len(prompt.confirmed) != 1 {
		t.Fatalf("expected one force confirmation, got %v", prompt.confirmed)
	}
	if !strings.Contains(out.String(), "cancelled delete") {
		t.Fatalf("expected cancellation notice, got %q", out.String())
	}
}

func TestDeleteNonEmptyQueueForcesWhenConfirmed(t *testing.T) {
	var forces []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		force := r.URL.Query().Get("force")
		forces = append(forces, force)
		if force == "false" {
			w.WriteHeader(http.StatusConflict)
		}
	}))
	defer server.Close()

	prompt := &scriptedPrompt{destinations: []string{"/queue/orders"}, confirms: []bool{true}}
	var out bytes.Buffer

	if err := deleteQueueInteractive(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if strings.Join(forces, ",") != "false,true" {
		t.Fatalf("expected a non-force attempt then a force delete, got %v", forces)
	}
	if !strings.Contains(out.String(), "deleted default:/queue/orders") {
		t.Fatalf("expected delete output, got %q", out.String())
	}
}

func TestCreateQueueUsesPromptedMaxPendingBytes(t *testing.T) {
	var maxPendingBytes string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		maxPendingBytes = r.URL.Query().Get("maxPendingBytes")
		_, _ = w.Write([]byte(`{"group":"default","destination":"/queue/orders","size":0,"pendingBytes":0,"maxPendingBytes":2048,"paused":false}`))
	}))
	defer server.Close()

	prompt := &scriptedPrompt{destinations: []string{"/queue/orders"}, maxPending: 2048}
	var out bytes.Buffer

	if err := createQueueInteractive(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if maxPendingBytes != "2048" {
		t.Fatalf("expected maxPendingBytes 2048, got %q", maxPendingBytes)
	}
	if !strings.Contains(out.String(), "created default:/queue/orders") {
		t.Fatalf("expected create output, got %q", out.String())
	}
}

func TestManagementLoopReportsActionErrorAndContinues(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()

	prompt := &scriptedPrompt{
		actions:      []string{managementPause, managementBack},
		destinations: []string{"/queue/missing"},
	}
	var out bytes.Buffer

	err := runManagementLoop(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out, false)

	if err != nil {
		t.Fatalf("expected the loop to survive an action error, got %v", err)
	}
	if !strings.Contains(out.String(), "error:") {
		t.Fatalf("expected an error notice, got %q", out.String())
	}
}

func TestResolveMonitorAddrFallsBackOnBlankInput(t *testing.T) {
	cases := []struct {
		name  string
		input string
		want  string
	}{
		{name: "empty", input: "", want: defaultAddr},
		{name: "whitespace", input: "   ", want: defaultAddr},
		{name: "trimmed", input: "  http://titan:9999  ", want: "http://titan:9999"},
	}

	for _, testCase := range cases {
		t.Run(testCase.name, func(t *testing.T) {
			if got := resolveMonitorAddr(testCase.input); got != testCase.want {
				t.Fatalf("expected %q, got %q", testCase.want, got)
			}
		})
	}
}

func TestResolveMaxPendingBytesFallsBackOnBlankInput(t *testing.T) {
	got, err := resolveMaxPendingBytes("  ")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != defaultMaxPendingBytes {
		t.Fatalf("expected %d, got %d", defaultMaxPendingBytes, got)
	}

	got, err = resolveMaxPendingBytes(" 2048 ")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != 2048 {
		t.Fatalf("expected 2048, got %d", got)
	}

	if _, err := resolveMaxPendingBytes("abc"); err == nil {
		t.Fatalf("expected a parse error for a non-numeric value")
	}
}

func TestOptionalPositiveNumberAcceptsBlankButRejectsInvalid(t *testing.T) {
	validate := optionalPositiveNumber("max pending bytes")

	if err := validate(""); err != nil {
		t.Fatalf("expected blank to be accepted, got %v", err)
	}
	if err := validate("1024"); err != nil {
		t.Fatalf("expected a positive number to be accepted, got %v", err)
	}
	if err := validate("0"); err == nil {
		t.Fatalf("expected zero to be rejected")
	}
	if err := validate("-1"); err == nil {
		t.Fatalf("expected a negative number to be rejected")
	}
}

func TestManagementActionsTargetThePromptedGroup(t *testing.T) {
	var queries []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		queries = append(queries, r.URL.Query().Get("group"))
	}))
	defer server.Close()

	prompt := &scriptedPrompt{
		actions:      []string{managementPause, managementBack},
		groups:       []string{"market"},
		destinations: []string{"/queue/orders"},
	}
	var out bytes.Buffer

	if err := runManagementLoop(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out, false); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if strings.Join(queries, ",") != "market" {
		t.Fatalf("expected the market group, got %v", queries)
	}
	if !strings.Contains(out.String(), "paused market:/queue/orders") {
		t.Fatalf("expected the group in the result, got %q", out.String())
	}
}

func TestManagementListFiltersByThePromptedGroup(t *testing.T) {
	var query string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		_, _ = w.Write([]byte(`[]`))
	}))
	defer server.Close()

	prompt := &scriptedPrompt{
		actions: []string{managementList, managementBack},
		groups:  []string{"market"},
	}
	var out bytes.Buffer

	if err := runManagementLoop(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out, false); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if query != "group=market" {
		t.Fatalf("expected a group filter, got %q", query)
	}
}

func TestManagementRejectsAMalformedGroupBeforeRequesting(t *testing.T) {
	var requests int
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
	}))
	defer server.Close()

	prompt := &scriptedPrompt{
		actions:      []string{managementPause, managementBack},
		groups:       []string{"bad/name"},
		destinations: []string{"/queue/orders"},
	}
	var out bytes.Buffer

	if err := runManagementLoop(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out, false); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if requests != 0 {
		t.Fatalf("expected no request, got %d", requests)
	}
	if !strings.Contains(out.String(), "invalid group name") {
		t.Fatalf("expected an invalid group notice, got %q", out.String())
	}
}

func TestForceDeleteRetriesTheSameGroup(t *testing.T) {
	var groups []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		groups = append(groups, r.URL.Query().Get("group"))
		if r.URL.Query().Get("force") == "false" {
			w.WriteHeader(http.StatusConflict)
		}
	}))
	defer server.Close()

	prompt := &scriptedPrompt{
		groups:       []string{"market"},
		destinations: []string{"/queue/orders"},
		confirms:     []bool{true},
	}
	var out bytes.Buffer

	if err := deleteQueueInteractive(context.Background(), monitor.NewClient(server.URL, ""), prompt, &out); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if strings.Join(groups, ",") != "market,market" {
		t.Fatalf("expected both attempts on the market group, got %v", groups)
	}
	if !strings.Contains(out.String(), "deleted market:/queue/orders") {
		t.Fatalf("expected the group in the result, got %q", out.String())
	}
}

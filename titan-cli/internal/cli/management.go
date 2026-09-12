package cli

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"

	"github.com/charmbracelet/huh"

	"github.com/traffic-hunter/titan/titan-cli/internal/monitor"
	"github.com/traffic-hunter/titan/titan-cli/internal/render"
)

// defaultMaxPendingBytes matches the queue create flag default.
const defaultMaxPendingBytes int64 = 64 * 1024 * 1024

const (
	managementList   = "list"
	managementCreate = "create"
	managementPause  = "pause"
	managementResume = "resume"
	managementPurge  = "purge"
	managementDelete = "delete"
	managementBack   = "back"
)

// managementPrompt supplies the interactive input the management menu needs.
//
// The menu depends on this interface rather than on huh directly so the action
// flows can be driven by a script in tests, where no terminal is attached.
type managementPrompt interface {
	Action() (string, error)
	// Group asks for a destination group and may answer with an empty string.
	// Listing reads that as every group; a change reads it as the default group.
	Group(title string) (string, error)
	Destination(title string) (string, error)
	MaxPendingBytes() (int64, error)
	Confirm(prompt string) (bool, error)
	Acknowledge() error
}

// filterGroup reads a list filter, where a blank answer means every group.
func filterGroup(value string) (string, error) {
	if strings.TrimSpace(value) == "" {
		return "", nil
	}
	return monitor.NormalizeGroup(value)
}

// targetGroup reads the group a change applies to, defaulting to the default group.
func targetGroup(prompt managementPrompt) (string, error) {
	value, err := prompt.Group("Group")
	if err != nil {
		return "", err
	}
	return monitor.NormalizeGroup(value)
}

// managementActionOptions lists the management submenu entries in display order.
func managementActionOptions() []huh.Option[string] {
	return []huh.Option[string]{
		huh.NewOption("List queues", managementList),
		huh.NewOption("Create queue", managementCreate),
		huh.NewOption("Pause queue", managementPause),
		huh.NewOption("Resume queue", managementResume),
		huh.NewOption("Purge queue", managementPurge),
		huh.NewOption("Delete queue", managementDelete),
		huh.NewOption("Back", managementBack),
	}
}

// runManagement collects the monitor connection settings once and then serves
// the management submenu until the user goes back.
func runManagement(stdin io.Reader, stdout io.Writer, noColor bool) error {
	addr, monitorToken, err := selectManagementConnection(stdin, stdout)
	if err != nil {
		if errors.Is(err, huh.ErrUserAborted) {
			return nil
		}
		return err
	}

	client := monitor.NewClient(addr, monitorToken)
	prompt := &huhManagementPrompt{stdin: stdin, stdout: stdout}
	return runManagementLoop(context.Background(), client, prompt, stdout, !noColor)
}

// runManagementLoop repeats the management submenu. It returns nil once the
// user selects Back or cancels the menu, so the caller can show the main menu
// again instead of exiting.
func runManagementLoop(
	ctx context.Context,
	client monitor.Client,
	prompt managementPrompt,
	out io.Writer,
	color bool,
) error {
	for {
		action, err := prompt.Action()
		if err != nil {
			if errors.Is(err, huh.ErrUserAborted) {
				return nil
			}
			return err
		}
		if action == managementBack {
			return nil
		}

		if err := runManagementAction(ctx, client, action, prompt, out, color); err != nil {
			if errors.Is(err, huh.ErrUserAborted) {
				continue
			}
			fmt.Fprintf(out, "error: %v\n", err)
		}
	}
}

func runManagementAction(
	ctx context.Context,
	client monitor.Client,
	action string,
	prompt managementPrompt,
	out io.Writer,
	color bool,
) error {
	switch action {
	case managementList:
		raw, err := prompt.Group("Group filter")
		if err != nil {
			return err
		}
		group, err := filterGroup(raw)
		if err != nil {
			return err
		}
		queues, err := client.Queues(ctx, group)
		if err != nil {
			return err
		}
		render.Queues(out, queues, render.Options{Color: color, Group: group})
		return prompt.Acknowledge()
	case managementCreate:
		return createQueueInteractive(ctx, client, prompt, out)
	case managementPause, managementResume:
		group, err := targetGroup(prompt)
		if err != nil {
			return err
		}
		destination, err := prompt.Destination("Destination")
		if err != nil {
			return err
		}
		if err := applyQueueAction(ctx, client, action, group, destination); err != nil {
			return err
		}
		fmt.Fprintf(out, "%sd %s\n", action, queueRef(group, destination))
		return nil
	case managementPurge:
		return purgeQueueInteractive(ctx, client, prompt, out)
	case managementDelete:
		return deleteQueueInteractive(ctx, client, prompt, out)
	default:
		return fmt.Errorf("unsupported management action %q", action)
	}
}

func createQueueInteractive(
	ctx context.Context,
	client monitor.Client,
	prompt managementPrompt,
	out io.Writer,
) error {
	group, err := targetGroup(prompt)
	if err != nil {
		return err
	}
	destination, err := prompt.Destination("Destination")
	if err != nil {
		return err
	}
	maxPendingBytes, err := prompt.MaxPendingBytes()
	if err != nil {
		return err
	}

	queue, err := client.CreateQueue(ctx, group, destination, maxPendingBytes)
	if err != nil {
		return err
	}
	fmt.Fprintf(
		out,
		"created %s size=%d pendingBytes=%d maxPendingBytes=%d paused=%t\n",
		queueRef(queue.Group, queue.Destination),
		queue.Size,
		queue.PendingBytes,
		queue.MaxPendingBytes,
		queue.Paused,
	)
	return nil
}

// purgeQueueInteractive asks for confirmation before dropping messages and
// leaves the queue untouched when the user declines.
func purgeQueueInteractive(
	ctx context.Context,
	client monitor.Client,
	prompt managementPrompt,
	out io.Writer,
) error {
	group, err := targetGroup(prompt)
	if err != nil {
		return err
	}
	destination, err := prompt.Destination("Destination")
	if err != nil {
		return err
	}
	queue := queueRef(group, destination)
	confirmed, err := prompt.Confirm(fmt.Sprintf("Remove every pending message from %s?", queue))
	if err != nil {
		return err
	}
	if !confirmed {
		fmt.Fprintf(out, "cancelled purge of %s\n", queue)
		return nil
	}

	if err := client.PurgeQueue(ctx, group, destination); err != nil {
		return err
	}
	fmt.Fprintf(out, "purged %s\n", queue)
	return nil
}

// deleteQueueInteractive deletes an empty queue directly. A queue that still
// holds messages is reported by the server as a conflict, and only then does
// this ask whether to drop those messages.
func deleteQueueInteractive(
	ctx context.Context,
	client monitor.Client,
	prompt managementPrompt,
	out io.Writer,
) error {
	group, err := targetGroup(prompt)
	if err != nil {
		return err
	}
	destination, err := prompt.Destination("Destination")
	if err != nil {
		return err
	}
	queue := queueRef(group, destination)

	err = client.DeleteQueue(ctx, group, destination, false)
	if err == nil {
		fmt.Fprintf(out, "deleted %s\n", queue)
		return nil
	}

	var httpErr monitor.HTTPError
	if !errors.As(err, &httpErr) || httpErr.StatusCode != http.StatusConflict {
		return err
	}

	confirmed, confirmErr := prompt.Confirm(
		fmt.Sprintf("%s still holds messages. Delete the queue and drop them?", queue),
	)
	if confirmErr != nil {
		return confirmErr
	}
	if !confirmed {
		fmt.Fprintf(out, "cancelled delete of %s\n", queue)
		return nil
	}

	// The retry names the same queue the conflict came from, never a different group.
	if err := client.DeleteQueue(ctx, group, destination, true); err != nil {
		return err
	}
	fmt.Fprintf(out, "deleted %s\n", queue)
	return nil
}

func selectManagementConnection(stdin io.Reader, stdout io.Writer) (string, string, error) {
	addr := defaultAddr
	monitorToken := token("")

	form := huh.NewForm(huh.NewGroup(
		huh.NewInput().
			Title("Monitor address").
			Description("Leave empty to use "+defaultAddr+".").
			Value(&addr),
		huh.NewInput().
			Title("Token").
			Description("Leave empty to use TITAN_MONITOR_TOKEN.").
			EchoMode(huh.EchoModePassword).
			Value(&monitorToken),
	)).WithInput(stdin).WithOutput(stdout).WithTheme(managementTheme())

	if err := form.Run(); err != nil {
		return "", "", err
	}
	return resolveMonitorAddr(addr), strings.TrimSpace(monitorToken), nil
}

// resolveMonitorAddr falls back to the default address when the input comes
// back empty. Some fallback terminals submit a prefilled input as an empty
// string, so requiring a non-blank value there would reject a plain Enter.
func resolveMonitorAddr(value string) string {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return defaultAddr
	}
	return trimmed
}

// resolveMaxPendingBytes applies the same empty input fallback as
// resolveMonitorAddr for the prefilled byte limit.
func resolveMaxPendingBytes(value string) (int64, error) {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return defaultMaxPendingBytes, nil
	}
	return strconv.ParseInt(trimmed, 10, 64)
}

// optionalPositiveNumber accepts an empty value so a prefilled default can be
// submitted unchanged, and otherwise requires a positive number.
func optionalPositiveNumber(name string) func(string) error {
	return func(value string) error {
		if strings.TrimSpace(value) == "" {
			return nil
		}
		return positiveNumber(name, false)(value)
	}
}

func managementTheme() *huh.Theme {
	theme := huh.ThemeCharm()
	theme.Focused.SelectSelector = theme.Focused.SelectSelector.SetString("● ")
	return theme
}

// huhManagementPrompt is the terminal-backed managementPrompt.
type huhManagementPrompt struct {
	stdin  io.Reader
	stdout io.Writer
}

func (p *huhManagementPrompt) Action() (string, error) {
	selected := managementList
	form := huh.NewForm(huh.NewGroup(
		huh.NewSelect[string]().
			Title("Queue management").
			Description("Use arrow keys to move and Enter to select.").
			Options(managementActionOptions()...).
			Value(&selected),
	)).WithInput(p.stdin).WithOutput(p.stdout).WithTheme(managementTheme())
	return selected, form.Run()
}

func (p *huhManagementPrompt) Group(title string) (string, error) {
	group := ""
	form := huh.NewForm(huh.NewGroup(
		huh.NewInput().
			Title(title).
			Description("Leave empty for the default group.").
			Value(&group).
			Validate(optionalGroup),
	)).WithInput(p.stdin).WithOutput(p.stdout).WithTheme(managementTheme())
	if err := form.Run(); err != nil {
		return "", err
	}
	return strings.TrimSpace(group), nil
}

func (p *huhManagementPrompt) Destination(title string) (string, error) {
	destination := ""
	form := huh.NewForm(huh.NewGroup(
		huh.NewInput().
			Title(title).
			Value(&destination).
			Validate(notBlank("destination")),
	)).WithInput(p.stdin).WithOutput(p.stdout).WithTheme(managementTheme())
	if err := form.Run(); err != nil {
		return "", err
	}
	return strings.TrimSpace(destination), nil
}

func (p *huhManagementPrompt) MaxPendingBytes() (int64, error) {
	value := strconv.FormatInt(defaultMaxPendingBytes, 10)
	form := huh.NewForm(huh.NewGroup(
		huh.NewInput().
			Title("Max pending bytes").
			Description("Leave empty to use " + value + ".").
			Value(&value).
			Validate(optionalPositiveNumber("max pending bytes")),
	)).WithInput(p.stdin).WithOutput(p.stdout).WithTheme(managementTheme())
	if err := form.Run(); err != nil {
		return 0, err
	}
	return resolveMaxPendingBytes(value)
}

func (p *huhManagementPrompt) Confirm(prompt string) (bool, error) {
	confirmed := false
	form := huh.NewForm(huh.NewGroup(
		huh.NewConfirm().
			Title(prompt).
			Affirmative("Yes").
			Negative("No").
			Value(&confirmed),
	)).WithInput(p.stdin).WithOutput(p.stdout).WithTheme(managementTheme())
	if err := form.Run(); err != nil {
		return false, err
	}
	return confirmed, nil
}

func (p *huhManagementPrompt) Acknowledge() error {
	proceed := true
	form := huh.NewForm(huh.NewGroup(
		huh.NewConfirm().
			Title("Press Enter to return to the management menu.").
			Affirmative("Back").
			Negative("").
			Value(&proceed),
	)).WithInput(p.stdin).WithOutput(p.stdout).WithTheme(managementTheme())
	return form.Run()
}

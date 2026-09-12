package monitor

import (
	"fmt"
	"regexp"
	"strings"
)

// DefaultGroup is the namespace a queue belongs to when no group is named.
const DefaultGroup = "default"

// groupPattern mirrors the server's group naming rule. A name the server would
// refuse is rejected here so a bad value never reaches a management request.
var groupPattern = regexp.MustCompile(`^[a-zA-Z0-9_-]{1,64}$`)

// ValidGroup reports whether name is a group name the server accepts.
func ValidGroup(name string) bool {
	return groupPattern.MatchString(name)
}

// NormalizeGroup resolves user input into the group a request targets.
//
// An empty or blank value means the default group, matching the server. Any
// other malformed name is an error rather than a silent fallback, because
// sending a request to the wrong queue is worse than refusing it.
func NormalizeGroup(name string) (string, error) {
	trimmed := strings.TrimSpace(name)
	if trimmed == "" {
		return DefaultGroup, nil
	}
	if !ValidGroup(trimmed) {
		return "", fmt.Errorf("invalid group name %q", name)
	}
	return trimmed, nil
}

// checkQueueContract rejects a response whose queues do not carry a usable group.
//
// The server and the CLI are released together, so a missing or malformed group
// means the two are out of step. Guessing "default" there would show one queue
// under another queue's name, so the mismatch is reported instead.
func checkQueueContract(queues []QueueSnapshot) error {
	for _, queue := range queues {
		if !ValidGroup(queue.Group) {
			return fmt.Errorf(
				"monitor returned queue %q with group %q; update the Titan server and CLI together",
				queue.Destination,
				queue.Group,
			)
		}
	}
	return nil
}

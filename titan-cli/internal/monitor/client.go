package monitor

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type Client struct {
	baseURL    string
	token      string
	httpClient *http.Client
}

type HTTPError struct {
	StatusCode int
	Status     string
}

func (e HTTPError) Error() string {
	return fmt.Sprintf("monitor endpoint returned %s", e.Status)
}

func NewClient(addr string, token string) Client {
	return NewClientWithTimeout(addr, token, 5*time.Second)
}

func NewClientWithTimeout(addr string, token string, timeout time.Duration) Client {
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	return Client{
		baseURL: strings.TrimRight(addr, "/"),
		token:   token,
		httpClient: &http.Client{
			Timeout: timeout,
		},
	}
}

func (c Client) Snapshot(ctx context.Context) (Snapshot, error) {
	request, err := c.request(ctx, http.MethodGet, "/titan/monitor/snapshot")
	if err != nil {
		return Snapshot{}, err
	}

	response, err := c.httpClient.Do(request)
	if err != nil {
		return Snapshot{}, err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return Snapshot{}, HTTPError{StatusCode: response.StatusCode, Status: response.Status}
	}

	var snapshot Snapshot
	if err := json.NewDecoder(response.Body).Decode(&snapshot); err != nil {
		return Snapshot{}, err
	}
	if err := checkQueueContract(snapshot.Queues); err != nil {
		return Snapshot{}, err
	}
	return snapshot, nil
}

// Queues lists dispatcher queues. An empty group lists every group, while a
// named group asks the server to return only that group's queues.
func (c Client) Queues(ctx context.Context, group string) ([]QueueSnapshot, error) {
	path := "/titan/monitor/queues"
	if group != "" {
		values := url.Values{}
		values.Set("group", group)
		path += "?" + values.Encode()
	}
	request, err := c.request(ctx, http.MethodGet, path)
	if err != nil {
		return nil, err
	}
	response, err := c.httpClient.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return nil, HTTPError{StatusCode: response.StatusCode, Status: response.Status}
	}

	var queues []QueueSnapshot
	if err := json.NewDecoder(response.Body).Decode(&queues); err != nil {
		return nil, err
	}
	if err := checkQueueContract(queues); err != nil {
		return nil, err
	}
	return queues, nil
}

// CreateQueue creates a queue inside group. The group is always sent, so a
// request never falls back to another namespace on the server.
func (c Client) CreateQueue(
	ctx context.Context,
	group string,
	destination string,
	maxPendingBytes int64,
) (QueueSnapshot, error) {
	values := url.Values{}
	values.Set("group", group)
	values.Set("destination", destination)
	if maxPendingBytes > 0 {
		values.Set("maxPendingBytes", fmt.Sprintf("%d", maxPendingBytes))
	}
	request, err := c.request(ctx, http.MethodPost, "/titan/monitor/queues?"+values.Encode())
	if err != nil {
		return QueueSnapshot{}, err
	}
	response, err := c.httpClient.Do(request)
	if err != nil {
		return QueueSnapshot{}, err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return QueueSnapshot{}, HTTPError{StatusCode: response.StatusCode, Status: response.Status}
	}

	var queue QueueSnapshot
	if err := json.NewDecoder(response.Body).Decode(&queue); err != nil {
		return QueueSnapshot{}, err
	}
	if err := checkQueueContract([]QueueSnapshot{queue}); err != nil {
		return QueueSnapshot{}, err
	}
	return queue, nil
}

func (c Client) DeleteQueue(ctx context.Context, group string, destination string, force bool) error {
	values := url.Values{}
	values.Set("group", group)
	values.Set("destination", destination)
	values.Set("force", fmt.Sprintf("%t", force))
	request, err := c.request(ctx, http.MethodDelete, "/titan/monitor/queues?"+values.Encode())
	if err != nil {
		return err
	}
	response, err := c.httpClient.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return HTTPError{StatusCode: response.StatusCode, Status: response.Status}
	}
	return nil
}

// PauseQueue manually pauses the queue for the destination.
//
// Pausing is idempotent: an already paused queue reports success.
func (c Client) PauseQueue(ctx context.Context, group string, destination string) error {
	return c.queueAction(ctx, "pause", group, destination)
}

// ResumeQueue clears the manual pause for the queue of the destination.
//
// A queue that is still under byte pressure stays paused by flow control.
func (c Client) ResumeQueue(ctx context.Context, group string, destination string) error {
	return c.queueAction(ctx, "resume", group, destination)
}

// PurgeQueue removes every pending message and keeps the queue itself.
func (c Client) PurgeQueue(ctx context.Context, group string, destination string) error {
	return c.queueAction(ctx, "purge", group, destination)
}

func (c Client) queueAction(ctx context.Context, action string, group string, destination string) error {
	values := url.Values{}
	values.Set("action", action)
	values.Set("group", group)
	values.Set("destination", destination)
	request, err := c.request(ctx, http.MethodPost, "/titan/monitor/queues?"+values.Encode())
	if err != nil {
		return err
	}
	response, err := c.httpClient.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return HTTPError{StatusCode: response.StatusCode, Status: response.Status}
	}
	return nil
}

func (c Client) request(ctx context.Context, method string, path string) (*http.Request, error) {
	request, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, nil)
	if err != nil {
		return nil, err
	}
	if c.token != "" {
		request.Header.Set("Authorization", "Bearer "+c.token)
	}
	return request, nil
}

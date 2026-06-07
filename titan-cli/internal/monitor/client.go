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
	return snapshot, nil
}

func (c Client) Queues(ctx context.Context) ([]QueueSnapshot, error) {
	request, err := c.request(ctx, http.MethodGet, "/titan/monitor/queues")
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
	return queues, nil
}

func (c Client) CreateQueue(ctx context.Context, destination string, capacity int) (QueueSnapshot, error) {
	values := url.Values{}
	values.Set("destination", destination)
	if capacity > 0 {
		values.Set("capacity", fmt.Sprintf("%d", capacity))
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
	return queue, nil
}

func (c Client) DeleteQueue(ctx context.Context, destination string, force bool) error {
	values := url.Values{}
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

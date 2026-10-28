package com.launchdarkly.sample;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Accepts connections and never answers, like a network that silently drops traffic to LaunchDarkly.
 * Point the SDK at {@link #uri()} to test the startup timeout.
 */
public final class HangingServer implements AutoCloseable {

	private final ServerSocket socket;
	private final List<Socket> connections = new CopyOnWriteArrayList<>();
	private final List<String> headerLines = new CopyOnWriteArrayList<>();

	public HangingServer() throws IOException {
		socket = new ServerSocket(0);
		Thread acceptor = new Thread(() -> {
			while (!socket.isClosed()) {
				try {
					Socket connection = socket.accept();
					connections.add(connection);
					record(connection);
				} catch (IOException closed) {
					return;
				}
			}
		}, "hanging-server");
		acceptor.setDaemon(true);
		acceptor.start();
	}

	/** Reads and keeps each request's header lines, then leaves the connection open, unanswered. */
	private void record(Socket connection) {
		Thread reader = new Thread(() -> {
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
					headerLines.add(line);
				}
			} catch (IOException closed) {
				// The test closed the connection.
			}
		}, "hanging-server-reader");
		reader.setDaemon(true);
		reader.start();
	}

	/** Every request and header line received so far, e.g. "X-LaunchDarkly-Tags: ...". */
	public List<String> headerLines() {
		return headerLines;
	}

	public URI uri() {
		return URI.create("http://127.0.0.1:" + socket.getLocalPort());
	}

	@Override
	public void close() throws IOException {
		for (Socket connection : connections) {
			connection.close();
		}
		socket.close();
	}

}

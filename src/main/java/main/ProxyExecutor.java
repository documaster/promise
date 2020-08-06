/**
 * Promise library
 * Copyright (C) 2020, Documaster AS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package main;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyExecutor.class);

	private final ExecutorService pool;

	private final ConcurrentLinkedQueue<Callable<Void>> taskQueue = new ConcurrentLinkedQueue<>();

	private final Semaphore sem;

	private final AtomicBoolean isPaused = new AtomicBoolean(false);

	public ProxyExecutor(int poolSize, ExecutorService backingPool) {

		this.sem = new Semaphore(poolSize);
		this.pool = backingPool;
	}

	public boolean isPaused() {

		return isPaused.get();
	}

	public void pause() {

		isPaused.set(true);
	}

	public void resume() {

		isPaused.set(false);

		for (int i = Math.min(taskQueue.size(), sem.availablePermits()); i > 0; i--) {

			runNext();
		}
	}

	public void submit(Callable<Void> task) {

		taskQueue.add(() -> {

			try {

				if (isPaused()) {

					submit(task);
					return null;
				}

				return task.call();

			} finally {

				LOGGER.debug("Task completed. Releasing semaphore; permits: {}", sem.availablePermits());
				sem.release();
				runNext();
			}
		});

		runNext();
	}

	private void runNext() {

		if (isPaused()) {

			LOGGER.debug("Paused; not attempting to acquire semaphore");
			return;
		}

		LOGGER.debug("Attempting to acquire semaphore; permits: {}", sem.availablePermits());

		if (sem.tryAcquire()) {

			LOGGER.debug("Acquire semaphore; permits: {}", sem.availablePermits());

			try {

				Callable<Void> task = taskQueue.poll();

				if (task == null) {

					LOGGER.debug("No tasks in queue. Releasing semaphore; permits: {}", sem.availablePermits());
					sem.release();
					return;
				}

				pool.submit(task);

			} catch (Exception e) {

				LOGGER.debug("Exception while submitting task! Releasing semaphore!", e);
				sem.release();
				throw e;
			}
		}
	}
}

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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import main.Promise;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PromiseTest {

	@Test
	public void promiseRetryCancel() throws Exception {

		final int[] numFails = { 0 };
		final int sleepMs = 500;

		Supplier<CompletableFuture<Void>> supplier = () -> {

			return CompletableFuture.supplyAsync(() -> {

				try {
					Thread.sleep(sleepMs);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}

				numFails[0] += 1;
				throw new RuntimeException("Failed!!!11");
			});
		};

		CompletableFuture<Void> retry = Promise.retry(supplier, 5);
		Thread.sleep(sleepMs * 2 + sleepMs / 2);
		retry.cancel(true);

		assertEquals(2, numFails[0]);
	}

	@Test
	public void promiseRetry() throws Exception {

		final int[] numFails = { 2 };

		Supplier<CompletableFuture<Void>> supplier = () -> {

			return Promise.wrapFuture(() -> {
				if (numFails[0] > 0) {
					--numFails[0];
					throw new RuntimeException("Failed");
				}

				return Promise.none();
			});
		};

		Throwable ex = null;
		try {
			Promise.retry(supplier, numFails[0]).join();
		} catch (Exception e) {
			ex = e;
		}

		assertNotNull(ex);

		numFails[0] = 2;

		// Assert that if we make enough retries, the promise succeeds and there is no exception
		Promise.retry(supplier, numFails[0] + 1).join();
	}

	@Test
	public void none() throws Exception {

		assertNull(Promise.none().join());
	}

	@Test
	public void empty() throws Exception {

		assertEquals(Promise.empty().join(), Optional.empty());
	}

	@Test
	public void completed() throws Exception {

		assertEquals(Promise.completed(100).join(), 100);
		Supplier<Integer> s = () -> { return 1000; };
		assertEquals(Promise.completed(s.get()).join(), 1000);
		assertNull(Promise.completed(Promise.none().join()).join());
	}

	@Test
	public void fail() throws Exception {

		Throwable ex = new Throwable("Error");
		CompletableFuture<Object> fail = Promise.fail(ex);

		assertNotNull(fail);
		assertTrue(fail.isCompletedExceptionally());
	}

	@Test
	public void runUntil() throws Exception {

		final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

		Supplier<Integer> supplier = () -> { return 1000; };

		// Set the time units to a small number so that the tests pass quickly
		Promise.runUntil(supplier, 1000, TimeUnit.MILLISECONDS, scheduledExecutor);

		assertFalse(scheduledExecutor.isShutdown());

		Thread.sleep(1000);

		assertTrue(scheduledExecutor.isShutdown());
	}

	@Test
	public void runAtMostUntil() throws Exception {

		final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

		Supplier<Integer> supplier = () -> { return 1000; };

		// Set the time units to a small number so that the tests pass quickly
		Promise.runAtMostUntil(supplier, 1000, 1000, TimeUnit.MILLISECONDS, scheduledExecutor);
		assertFalse(scheduledExecutor.isShutdown());
		Thread.sleep(1000);
		assertTrue(scheduledExecutor.isShutdown());

		Promise.runAtMostUntil(supplier, 1000, 100, TimeUnit.MILLISECONDS, scheduledExecutor);
		assertTrue(scheduledExecutor.isShutdown());

		Promise.runAtMostUntil(supplier, 1000, 10000, TimeUnit.MILLISECONDS, scheduledExecutor);
		assertTrue(scheduledExecutor.isShutdown());

		Promise.runAtMostUntil(supplier, 100, 1000, TimeUnit.MILLISECONDS, scheduledExecutor);
		assertTrue(scheduledExecutor.isShutdown());

		Promise.runAtMostUntil(supplier, 10000, 1000, TimeUnit.MILLISECONDS, scheduledExecutor);
		assertTrue(scheduledExecutor.isShutdown());
	}

	@Test
	public void delay() throws Exception {

		final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

		Supplier<Integer> supplier = () -> { return 0; };

		// Set the time units to a small number so that the tests pass quickly
		CompletableFuture<Void> delay = Promise.delay(1000, TimeUnit.MILLISECONDS, scheduledExecutor);
		delay.thenRun(() -> supplier.get());

		for (int i = 0; i < 1000; i++) {
			Thread.sleep(1);
		}

		assertTrue(delay.isDone());
	}

	@Test
	public void delaySupplier() throws Exception {

		final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

		Supplier<Integer> supplier = () -> { return 1000; };

		// Set the time units to a small number so that the tests pass quickly
		CompletableFuture<Integer> delay = Promise.delay(supplier, 1000, TimeUnit.MILLISECONDS, scheduledExecutor);

		int delayCounter = 0;
		for (int i = 0; i < 1000; i++) {
			Thread.sleep(1);
			delayCounter += 1;
		}

		assertTrue(delay.isDone());
		assertEquals(delay.get(), delayCounter);
	}
}

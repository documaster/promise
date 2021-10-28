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
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import main.Promise;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromiseTest {

	@Test
	void nil() throws Exception {

		Promise<Void> p = new Promise<>();
		p.nil();

		assertNull(p.join());
	}

	@Test
	void blank() throws Exception {

		Promise<Optional<Object>> blank = Promise.blank();

		assertFalse(blank.join().isPresent());
	}

	@Test
	void abort() throws Throwable {

		Promise<Void> p = new Promise<>();
		p.abort(new NullPointerException());

		assertThrows(CompletionException.class, () -> p.join());
	}

	@Test
	public void promiseRetryCancel() throws Exception {

		final int[] numFails = { 0 };
		final int sleepMs = 500;

		Supplier<CompletableFuture<Void>> supplier = () -> {

			return CompletableFuture.supplyAsync(() -> {

				sleep(sleepMs);

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
	void promiseRetry() throws Exception {

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
	void none() throws Exception {

		assertNull(Promise.none().join());
	}

	@Test
	void empty() throws Exception {

		assertEquals(Promise.empty().join(), Optional.empty());
	}

	@Test
	void completed() throws Exception {

		assertEquals(Promise.completed(100).join(), 100);
		Supplier<Integer> s = () -> { return 1000; };
		assertEquals(Promise.completed(s.get()).join(), 1000);
		assertNull(Promise.completed(Promise.none().join()).join());
	}

	@Test
	void fail() throws Exception {

		Throwable ex = new Throwable("Error");
		CompletableFuture<Object> fail = Promise.fail(ex);

		assertNotNull(fail);
		assertTrue(fail.isCompletedExceptionally());
	}

	@Test
	void runUntil() throws Exception {

		final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

		Supplier<Integer> supplier = () -> { return 1000; };

		// Set the time units to a small number so that the tests pass quickly
		Promise.runUntil(supplier, 1000, TimeUnit.MILLISECONDS, scheduledExecutor);

		assertFalse(scheduledExecutor.isShutdown());

		Thread.sleep(1000);

		assertTrue(scheduledExecutor.isShutdown());
	}

	@Test
	void runAtMostUntil() throws Exception {

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
	void delay() throws Exception {

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
	void delaySupplier() throws Exception {

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

	@Test
	void runBothThenApply() throws Exception {

		AtomicInteger sum = new AtomicInteger(0);

		Supplier<Integer> supplier1 = () -> { sleep(500); return 100; };
		Supplier<Integer> supplier2 = () -> { sleep(500); return 100; };

		CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(supplier1);
		CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(supplier2);

		CompletableFuture<Integer> intResult = Promise.runBothThenApply(
				future1,
				future2,
				(int1, int2) -> CompletableFuture.supplyAsync(() -> sum.updateAndGet((dummy) -> int1 + int2)));

		assertFalse(intResult.isDone());
		assertEquals(0, sum.get());

		Thread.sleep(1000);

		assertTrue(intResult.isDone());
		assertEquals(200, intResult.join().intValue());
	}

	@Test
	void runBothSuppliersThenApply() throws Exception {

		AtomicInteger sum = new AtomicInteger(0);

		Supplier<Integer> supplier1 = () -> { sleep(500); return 100; };
		Supplier<Integer> supplier2 = () -> { sleep(500); return 100; };

		CompletableFuture<Integer> intResult = Promise.runBothThenApply(
				supplier1,
				supplier2,
				(int1, int2) -> CompletableFuture.supplyAsync(() -> sum.updateAndGet((dummy) -> int1 + int2)));

		assertFalse(intResult.isDone());
		assertEquals(0, sum.get());

		Thread.sleep(1000);

		assertTrue(intResult.isDone());
		assertEquals(200, intResult.join().intValue());
	}

	@Test
	void throwNPE() throws Throwable {

		assertThrows(NullPointerException.class, () -> {

			Promise.runUntil(null, 1000, null, null);
		});
	}

	@Test
	void run() throws Exception {

		CompletableFuture<Integer> cf = Promise.completed(1000);
		CompletableFuture<Void> completed = Promise.run(cf);
		completed.join();
		assertTrue(completed.isDone());
	}

	private void sleep(long millis) {

		try {

			Thread.sleep(millis);

		} catch (InterruptedException e) {

			throw new RuntimeException(e);
		}
	}
}

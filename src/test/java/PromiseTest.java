import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import main.Promise;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PromiseTest {

	@Test
	public void testPromiseRetryCancel() throws Exception {

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
	public void testPromiseRetry() throws Exception {

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

}

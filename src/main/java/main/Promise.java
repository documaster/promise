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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Promise {

	private static final Logger LOGGER = LoggerFactory.getLogger(Promise.class);

	public static <T> CompletableFuture<T> none() {

		return completed(null);
	}

	public static <T> CompletableFuture<Optional<T>> empty() {

		return completed(Optional.empty());
	}

	public static <T> CompletableFuture<T> completed(T t) {

		return CompletableFuture.completedFuture(t);
	}

	public static <T> CompletableFuture<T> fail(Throwable e) {

		CompletableFuture<T> cf = new CompletableFuture<T>();
		cf.completeExceptionally(e);
		return cf;
	}

	/**
	 * Wrap a function returning a CompletableFuture so that any exceptions thrown will get properly propagated as a
	 * failed future to users of this function. That way users of the function will not have to handle both exceptions
	 * with regular try-catch logic and handle a failed future.
	 *
	 * @param supplier
	 * 		The function returning a CompletableFuture which we want to wrap
	 * @return A CompletableFuture that will complete normally when the future returned by the supplier completes
	 * normally, and will complete exceptionally when the future returned is completed exceptionally OR the supplier
	 * generates an exception.
	 */
	public static <T> CompletableFuture<T> wrapFuture(Supplier<CompletableFuture<T>> supplier) {

		return none().thenCompose(dummy -> supplier.get());
	}

	public static <T> CompletableFuture<T> wrap(Supplier<T> supplier) {

		return none().thenApply(dummy -> supplier.get());
	}

	/**
	 * Retry a CompletableFuture returned by a supplier until it completes with no exception or the maximum number of
	 * retries is reached.
	 *
	 * @param promiseSupplier
	 * 		A function responsible for constructing the CompletableFuture that will be retried.
	 * @param numRetries
	 * 		The maximum number of times to try obtaining and executing a promise returned by the promiseSupplier.
	 * @return A CompletableFuture that will complete exceptionally if the future returned by the promiseSupplier has
	 * completed exceptionally numRetries times and will complete with the result of the fitire returned by the
	 * promiseSupplier otherwise.
	 */
	public static <T> CompletableFuture<T> retry(Supplier<CompletableFuture<T>> promiseSupplier, int numRetries) {

		CompletableFuture<T> promise = new CompletableFuture<>();

		retryHelper(promiseSupplier, numRetries - 1, promise);

		return promise;
	}

	private static <T> void retryHelper(
			Supplier<CompletableFuture<T>> promiseSupplier, int numRetries, CompletableFuture<T> promise) {

		CompletableFuture<T> retryPromise = promiseSupplier.get();

		promise.whenComplete((t, ex) -> {
			if (ex != null) {
				retryPromise.completeExceptionally(ex);
			}
		});

		retryPromise.whenComplete((result, ex) -> {
			if (ex == null) {
				promise.complete(result);
			} else if (numRetries > 0) {
				int numRetriesLeft = numRetries - 1;
				LOGGER.info("Promise completed exceptionally. Retrying... (retries left {}). ", numRetriesLeft);
				retryHelper(promiseSupplier, numRetriesLeft, promise);
			} else {
				promise.completeExceptionally(ex);
			}
		});
	}

	public static CompletableFuture<Void> delay(long timeout, TimeUnit unit, ScheduledExecutorService delayer) {

		CompletableFuture<Void> result = new CompletableFuture<>();
		delayer.schedule(() -> result.complete(null), timeout, unit);
		return result;
	}

	@SafeVarargs
	public static <T> CompletableFuture<List<T>> allOf(CompletableFuture<T>... futures) {

		return allOf(Arrays.asList(futures));
	}

	public static <T> CompletableFuture<List<T>> allOf(Collection<CompletableFuture<T>> futures) {

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
				.thenApply((dummy) -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
	}

	public static <T> CompletableFuture<List<T>> allOf(Stream<CompletableFuture<T>> futures) {

		return allOf(futures.collect(Collectors.toList()));
	}

	@SafeVarargs
	public static <T> CompletableFuture<Void> failFastAllOf(CompletableFuture<T>... futures) {

		CompletableFuture<Void> voidCompletableFuture = CompletableFuture.allOf(futures);

		Stream.of(futures).forEach(f -> f.exceptionally(e -> {

			voidCompletableFuture.completeExceptionally(e);
			return null;
		}));

		voidCompletableFuture.exceptionally(e -> {

			Stream.of(futures).forEach(f -> f.cancel(true));
			return null;
		});

		return voidCompletableFuture;
	}

	public static FuturesListBuilder waitFor() {

		return new FuturesListBuilder();
	}

	public static class FuturesListBuilder {

		private final List<CompletableFuture<?>> futures = new LinkedList<>();

		public FuturesListBuilder add(CompletableFuture<?> cf) {
			futures.add(cf);
			return this;
		}

		public <T> FuturesListBuilder add(List<CompletableFuture<T>> futuresList) {
			futures.addAll(futuresList);
			return this;
		}

		public final <T> FuturesListBuilder add(CompletableFuture<T>... futures) {

			return add(Arrays.asList(futures));
		}

		public CompletableFuture<Void> all() {

			return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		}
	}

	/**
	 * Sequentially apply an action to several items.
	 *
	 * @param itr
	 * 		Iterator that returns the items to process.
	 * @param action
	 * 		Action to apply to each element.
	 * @param <T>
	 * 		Type of each item to process.
	 * @param <V>
	 * 		Type of the CompletableFuture returned by the action
	 * @return A promise that will be fulfilled when each item returned by the iterator has been processed by the
	 * provided action. CompletableFuture<Void> is returned, regardless of the type returned by the iterator function.
	 */
	public static <T, V> CompletableFuture<Void> doSequentially(
			Iterator<T> itr, Function<T, CompletableFuture<V>> action) {

		if (itr.hasNext() == false) {

			return none();
		}

		return action.apply(itr.next()).thenCompose(dummy -> doSequentially(itr, action));
	}

	/**
	 * Sequentially apply an action to several items and accumulate the results of each action execution.
	 *
	 * @param itr
	 * 		Iterator that returns the items to process.
	 * @param action
	 * 		Action to apply to each element.
	 * @param initialVal
	 * 		The initial value passed into the accumulator function
	 * @param accumulator
	 * 		Function that should accumulate/combine successive results return from each action.
	 * 		The first argument passed each time will be the value returned by the accumulator from
	 * 		the previous iteration (or the initialVal for the very first run). The second argument
	 * 		passed will be the result returned by {@code action} in the current iteration.
	 * 		The function should return a "sum"/"combination" of  the old and new value.
	 * @param <T>
	 * 		The type of each item. {@code action} should be able to process objects of this type.
	 * @param <U>
	 * 		"return value" type of executing the {@code action}
	 * @param <V>
	 * 		The type of the accumulated-into value. Could be, for example, {@code List<U>} or same as
	 * 		{@code <U>} or an entirely different type depending on how you wish to accumulate results.
	 * @return The accumulated value.
	 */
	public static <T, U, V> CompletableFuture<V> doSequentially(
			Iterator<T> itr,
			Function<T, CompletableFuture<U>> action,
			V initialVal,
			BiFunction<V, U, V> accumulator) {

		if (itr.hasNext() == false) {

			return completed(initialVal);
		}

		return action.apply(itr.next())
				.thenCompose(result -> doSequentially(itr, action, accumulator.apply(initialVal, result), accumulator));
	}

	/**
	 * Asynchronously execute the given supplier utilizing the provided executorService
	 *
	 * @param supplier A supplier function the user wishes to execute asynchronously
	 * @param executorService The ProxyExecutor to utilize
	 * @param <T> The type of value provided by the supplier
	 * @return A CompletableFuture which will be resolved with the value provided by executing the supplier
	 */
	public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, ProxyExecutor executorService) {

		CompletableFuture<T> retval = new CompletableFuture<>();

		executorService.submit(() -> {

			try {

				retval.complete(supplier.get());

			} catch (Exception e) {

				retval.completeExceptionally(e);
			}

			return null;
		});

		return retval;
	}

	public static CompletableFuture<Void> supplyAsync(Procedure proc, ProxyExecutor executorService) {

		return supplyAsync(() -> {

			proc.run();
			return null;

		}, executorService);
	}

	public static <T> void noop(T dummy) {
		// Do nothing
	}

	/**
	 * Synchronizes supplier's calls as they would be chained and executed sequentially! Limits the execution of the
	 * given block calls to one per time. Simply creates a placeholder CompletableFuture and uses is to store currently
	 * running task. All of the other calls are chained for future execution.
	 *
	 * Squashes any previously risen exceptions as this is viral for the current execution to take place.
	 * If an exception has appeared it should be handled in the caller chain.
	 *
	 * @param supplier An asynchronous line function supplier
	 * @param <T> The type of value provided by the supplier
	 * @return A CompletableFuture which will be resolved with the value provided by executing the supplier
	 */
	public static class SynchronizationManager<T> {

		private CompletableFuture<T> synchronizedCompletableFuture = none();

		public synchronized CompletableFuture<T> synchronize(Supplier<CompletableFuture<T>> supplier) {

			synchronizedCompletableFuture = synchronizedCompletableFuture
					.handle((aVoid, throwable) -> null)
					.thenCompose(aVoid -> supplier.get());

			return synchronizedCompletableFuture;
		}
	}

	@FunctionalInterface
	public interface Procedure {

		void run();
	}
}

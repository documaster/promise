package main;

public class PromiseException extends RuntimeException {

	private static final long serialVersionUID = -6451325185311169128L;

	public PromiseException(String message) {

		super(message);
	}

	public PromiseException(String message, Throwable cause) {

		super(message, cause);
	}

	public PromiseException(Throwable cause) {

		super(cause);
	}

	public PromiseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {

		super(message, cause, enableSuppression, writableStackTrace);
	}

	public static RuntimeException wrapIfNeeded(Throwable throwable) {

		if (throwable instanceof RuntimeException) {

			return (RuntimeException) throwable;
		}

		return new PromiseException(throwable);
	}
}

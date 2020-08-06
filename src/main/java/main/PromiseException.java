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

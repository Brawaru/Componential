package io.github.brawaru.componential;

/**
 * Represents a specific scenario exception handler
 *
 * @param <E> exception that may be thrown as a result of check
 */
public interface ExceptionHandler<E extends Throwable> {
  /**
   * Method called to resolve arisen exception in a more peaceful manner
   *
   * <p>There are three resolutions to the exception:
   *
   * <ul>
   *   <li>throw exception itself to propagate in the current call
   *   <li>return {@code false} to signal for cease of any further activity (for example, to break
   *       out of the loop)
   *   <li>Return {@code true} to continue with basic exception handling (for example, ignore
   *       exception and continue the loop)
   * </ul>
   *
   * @param exception exception that arisen and must be handled
   * @throws E may throw exception to propagate in the current call
   * @return {@code true} if execution should continue, otherwise {@code false}
   */
  boolean shouldContinue(E exception) throws E;
}

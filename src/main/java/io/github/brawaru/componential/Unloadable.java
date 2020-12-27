package io.github.brawaru.componential;

/**
 * Represents an unloadable component
 * <p>
 * Any component is de facto unloadable, so you don't have to implement this interface in every component you create,
 * it is only useful to implement when you have to do clean-up in case component gets unloaded
 */
public interface Unloadable {
  /**
   * Method to call before component is finally unloaded
   */
  void unload();
}

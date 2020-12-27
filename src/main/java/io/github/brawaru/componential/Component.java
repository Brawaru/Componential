package io.github.brawaru.componential;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents plugin's component
 * @param <P> Type of plugin that this component accepts
 */
public abstract class Component<P extends JavaPlugin> {

  @Nullable
  P plugin;

  /**
   * Plugin for which this component was initialized
   * @return plugin instance
   * @throws IllegalStateException If this method was called during the construction phase before the component has been completely initialised
   */
  @NotNull
  protected P getPlugin() {
    if (plugin == null) {
      throw new IllegalStateException("Property accessed before it was initialised");
    }

    return plugin;
  }
}

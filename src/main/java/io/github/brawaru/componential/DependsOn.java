package io.github.brawaru.componential;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.annotation.*;

/**
 * Annotates the component that depends on another component
 */
@Target(ElementType.TYPE)
@Repeatable(Dependencies.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface DependsOn {
  Class<? extends Component<? extends JavaPlugin>> value();
}
